package relation

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const createdJSON = `{"data":{"id":"` + clitest.RelationID + `","name":"Acme","type":"LEAD"}}`

func TestCreateSendsStdinBytesUnchanged(t *testing.T) {
	api := relationFixture(t)
	api.Respond("POST /otak/relations", http.StatusCreated, createdJSON)

	in := `{"name": "Acme B.V.",  "type": "LEAD", "expectedValue": 25000.10, "stage": "WARM"}`
	out, _, err := runCLI(t, in, "otak", "relation", "create")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Created relation "+clitest.RelationID) {
		t.Errorf("output = %q", out)
	}
	req := api.Request("POST /otak/relations")
	if !strings.Contains(req.Body, `"expectedValue":25000.10`) {
		t.Errorf("number was re-encoded: %s", req.Body)
	}
	var sent, given map[string]json.RawMessage
	if err := json.Unmarshal([]byte(req.Body), &sent); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal([]byte(in), &given); err != nil {
		t.Fatal(err)
	}
	for k, v := range given {
		if string(sent[k]) != string(v) {
			t.Errorf("key %s: sent %s, given %s", k, sent[k], v)
		}
	}
	if len(sent) != len(given) {
		t.Errorf("sent %d keys, given %d", len(sent), len(given))
	}
	if req.Org != clitest.OrgIDAcme {
		t.Errorf("org header = %q", req.Org)
	}
	if req.Auth != "Bearer access-token" {
		t.Errorf("auth header = %q", req.Auth)
	}
}

func TestCreateUnknownKey(t *testing.T) {
	tests := []struct {
		name, in, want string
	}{
		{"with suggestion", `{"name":"x","type":"LEAD","contry":"NL"}`, `unknown field "contry". Did you mean "country"?`},
		{"without suggestion", `{"name":"x","type":"LEAD","zzzzzzzzzz":1}`, `unknown field "zzzzzzzzzz"`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := relationFixture(t)
			_, _, err := runCLI(t, tt.in, "otak", "relation", "create")
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("write calls were made: %v", got)
			}
		})
	}
}

func TestCreateRejectsBadStdin(t *testing.T) {
	tests := []struct {
		name, in, want string
	}{
		{"empty", "  \n", "no JSON on stdin"},
		{"invalid", `{"name":`, "stdin is not valid JSON"},
		{"array", `[1]`, "not an array"},
		{"string", `"x"`, "not a string"},
		{"two objects", `{"name":"a"}{"name":"b"}`, "stdin is not valid JSON"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := relationFixture(t)
			_, _, err := runCLI(t, tt.in, "otak", "relation", "create")
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls were made: %v", got)
			}
		})
	}
}

func TestCreateOnTerminalFailsAtOnce(t *testing.T) {
	relationFixture(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	_, _, err := runCLI(t, "", "otak", "relation", "create")
	if err == nil || !strings.Contains(err.Error(), "no JSON on stdin") {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateWithoutSessionOrOrganization(t *testing.T) {
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	_, _, err := runCLI(t, `{"name":"x"}`, "otak", "relation", "create")
	if err == nil || err.Error() != "You are not logged in. Run 'oba login'." {
		t.Fatalf("err = %v", err)
	}

	api := clitest.NewMockAPI(t)
	clitest.LoginTo(t, api.URL(), "")
	_, _, err = runCLI(t, `{"name":"x"}`, "otak", "relation", "create")
	if err != session.ErrNoOrganization {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateDocsUnavailable(t *testing.T) {
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /v3/api-docs", http.StatusInternalServerError, "boom")
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	_, _, err := runCLI(t, `{"name":"x","type":"LEAD"}`, "otak", "relation", "create")
	if err == nil || !strings.Contains(err.Error(), "Could not read the field list from "+api.URL()+"/v3/api-docs") {
		t.Fatalf("err = %v", err)
	}
}

// updateRoutes serves the relation, then answers each write with the relation after it.
func updateRoutes(api *clitest.MockAPI, typ, stage string) {
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON(typ, stage))
	api.Respond("PUT "+clitest.RelationPath, http.StatusOK, detailJSON(typ, stage))
}

func TestUpdateKeepsUnsetFieldsAndNullClears(t *testing.T) {
	api := relationFixture(t)
	updateRoutes(api, "LEAD", "NEW")

	_, _, err := runCLI(t, `{"country": null, "expectedValue": 25000.10}`, "otak", "relation", "update", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	if got := api.Calls(); len(got) != 2 || got[0] != "GET "+clitest.RelationPath || got[1] != "PUT "+clitest.RelationPath {
		t.Fatalf("calls = %v", got)
	}
	var body map[string]json.RawMessage
	if err := json.Unmarshal([]byte(api.Request("PUT "+clitest.RelationPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	want := map[string]string{
		"name": `"Acme"`, "country": "null", "size": `"SIZE_1_10"`, "source": `"WEBSITE"`,
		"ownerUserId": `"` + ownerUserID + `"`, "expectedValue": "25000.10",
	}
	if len(body) != len(want) {
		t.Errorf("body keys = %v", body)
	}
	for k, v := range want {
		if string(body[k]) != v {
			t.Errorf("%s = %s, want %s", k, body[k], v)
		}
	}
}

func TestUpdateSkipsEqualTypeAndStage(t *testing.T) {
	api := relationFixture(t)
	updateRoutes(api, "LEAD", "WARM")

	out, _, err := runCLI(t, `{"type": "lead", "stage": "WARM"}`, "otak", "relation", "update", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	if got := api.Calls(); len(got) != 1 || got[0] != "GET "+clitest.RelationPath {
		t.Fatalf("calls = %v", got)
	}
	if !strings.Contains(out, "Nothing to update: relation "+clitest.RelationID+" already has these values.") {
		t.Errorf("output = %q", out)
	}
}

func TestUpdateOrdersPutTypeStage(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
	api.Respond("PUT "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
	api.Respond("PATCH "+clitest.RelationPath+"/type", http.StatusOK, detailJSON("CUSTOMER", ""))
	api.Respond("PATCH "+clitest.RelationPath+"/stage", http.StatusOK, detailJSON("CUSTOMER", "WARM"))

	out, _, err := runCLI(t, `{"country":"DE","type":"CUSTOMER","stage":"WARM"}`, "otak", "relation", "update", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"GET " + clitest.RelationPath, "PUT " + clitest.RelationPath, "PATCH " + clitest.RelationPath + "/type", "PATCH " + clitest.RelationPath + "/stage"}
	if got := api.Calls(); strings.Join(got, "|") != strings.Join(want, "|") {
		t.Fatalf("calls = %v, want %v", got, want)
	}
	if body := api.Request("PATCH " + clitest.RelationPath + "/type").Body; body != `{"type":"CUSTOMER"}` {
		t.Errorf("type body = %s", body)
	}
	if body := api.Request("PATCH " + clitest.RelationPath + "/stage").Body; body != `{"stage":"WARM"}` {
		t.Errorf("stage body = %s", body)
	}
	if !strings.Contains(out, "Updated relation "+clitest.RelationID) {
		t.Errorf("output = %q", out)
	}
}

func TestUpdateClosedStageConvertsAndSaysSo(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "WARM"))
	api.Respond("PATCH "+clitest.RelationPath+"/stage", http.StatusOK, detailJSON("CUSTOMER", "CLOSED"))

	// relationFixture pipes stdin, so a prompt would fail the run: the conversion asks nothing.
	out, _, err := runCLI(t, `{"stage":"CLOSED"}`, "otak", "relation", "update", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	want := "Updated relation " + clitest.RelationID + "\n" +
		"Relation " + clitest.RelationID + " is now a CUSTOMER at stage CLOSED; it is no longer a lead.\n"
	if out != want {
		t.Errorf("output = %q, want %q", out, want)
	}
	if body := api.Request("PATCH " + clitest.RelationPath + "/stage").Body; body != `{"stage":"CLOSED"}` {
		t.Errorf("stage body = %s", body)
	}
}

func TestUpdateWithoutTypeChangePrintsNoConversionLine(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
	api.Respond("PATCH "+clitest.RelationPath+"/stage", http.StatusOK, detailJSON("LEAD", "PROPOSAL"))

	out, _, err := runCLI(t, `{"stage":"PROPOSAL"}`, "otak", "relation", "update", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	if want := "Updated relation " + clitest.RelationID + "\n"; out != want {
		t.Errorf("output = %q, want %q", out, want)
	}
}

func TestConversionLine(t *testing.T) {
	relation := func(typ, stage string) client.Relation {
		r := client.Relation{ID: clitest.RelationID, Type: typ}
		if stage != "" {
			r.Stage = &stage
		}
		return r
	}
	tests := []struct {
		name          string
		before, after client.Relation
		want          string
	}{
		{"lead to customer", relation("LEAD", "PROPOSAL"), relation("CUSTOMER", "CLOSED"),
			"Relation " + clitest.RelationID + " is now a CUSTOMER at stage CLOSED; it is no longer a lead."},
		{"customer to lead", relation("CUSTOMER", "DIRECT"), relation("LEAD", "NEW"),
			"Relation " + clitest.RelationID + " is a LEAD again and restarted at stage NEW."},
		{"supplier to customer", relation("SUPPLIER", ""), relation("CUSTOMER", "DIRECT"),
			"Relation " + clitest.RelationID + " is now a CUSTOMER at stage DIRECT."},
		{"lead to supplier", relation("LEAD", "WARM"), relation("SUPPLIER", ""),
			"Relation " + clitest.RelationID + " is now a SUPPLIER; its stage is cleared."},
		{"customer to supplier", relation("CUSTOMER", "CLOSED"), relation("SUPPLIER", ""),
			"Relation " + clitest.RelationID + " is now a SUPPLIER; its stage is cleared."},
		{"no type change", relation("LEAD", "WARM"), relation("LEAD", "PROPOSAL"), ""},
		{"same type", relation("CUSTOMER", "DIRECT"), relation("customer", "DIRECT"), ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := conversionLine(tt.before, tt.after); got != tt.want {
				t.Errorf("conversionLine = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestUpdateNoPutForTypeOrStageAlone(t *testing.T) {
	tests := []struct {
		name, in, patch string
	}{
		{"type alone", `{"type":"SUPPLIER"}`, "/type"},
		{"stage alone", `{"stage":"PROPOSAL"}`, "/stage"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := relationFixture(t)
			api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
			api.Respond("PATCH "+clitest.RelationPath+tt.patch, http.StatusOK, detailJSON("SUPPLIER", "PROPOSAL"))
			if _, _, err := runCLI(t, tt.in, "otak", "relation", "update", clitest.RelationID); err != nil {
				t.Fatal(err)
			}
			want := "GET " + clitest.RelationPath + "|PATCH " + clitest.RelationPath + tt.patch
			if got := strings.Join(api.Calls(), "|"); got != want {
				t.Errorf("calls = %s, want %s", got, want)
			}
		})
	}
}

func TestUpdateStageComparedAfterTypeStep(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "WARM"))
	// The type change clears the stage, so the stage given afterwards is not equal any more.
	api.Respond("PATCH "+clitest.RelationPath+"/type", http.StatusOK, detailJSON("SUPPLIER", ""))
	api.Respond("PATCH "+clitest.RelationPath+"/stage", http.StatusOK, detailJSON("SUPPLIER", "WARM"))
	if _, _, err := runCLI(t, `{"type":"SUPPLIER","stage":"WARM"}`, "otak", "relation", "update", clitest.RelationID); err != nil {
		t.Fatal(err)
	}
	if got := len(api.Calls()); got != 3 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestUpdateFailureNamesAppliedSteps(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
	api.Respond("PUT "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
	api.Respond("PATCH "+clitest.RelationPath+"/type", http.StatusOK, detailJSON("CUSTOMER", ""))
	api.Respond("PATCH "+clitest.RelationPath+"/stage", http.StatusBadRequest,
		`{"error":{"code":"OTK_STA_002","message":"stage not allowed","errorValues":{"stage":"bad"}}}`)

	_, _, err := runCLI(t, `{"country":"DE","type":"CUSTOMER","stage":"WARM"}`, "otak", "relation", "update", clitest.RelationID)
	if err == nil {
		t.Fatal("expected an error")
	}
	for _, want := range []string{"OTK_STA_002: stage not allowed", "The update stopped at the stage (PATCH stage).",
		"Already applied: the fields (PUT), the type (PATCH type)."} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("error %q lacks %q", err, want)
		}
	}
	if client.ExitCode(err) != 1 {
		t.Errorf("exit code = %d", client.ExitCode(err))
	}
}

func TestUpdateFirstStepFailureChangedNothing(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK, detailJSON("LEAD", "NEW"))
	api.Respond("PUT "+clitest.RelationPath, http.StatusForbidden, `{}`)
	_, _, err := runCLI(t, `{"country":"DE"}`, "otak", "relation", "update", clitest.RelationID)
	if err == nil || !strings.Contains(err.Error(), "Nothing was changed.") {
		t.Fatalf("err = %v", err)
	}
	if client.ExitCode(err) != 3 {
		t.Errorf("exit code = %d, want 3", client.ExitCode(err))
	}
}

func TestUpdateEmptyObjectAndUnknownKey(t *testing.T) {
	api := relationFixture(t)
	_, _, err := runCLI(t, `{}`, "otak", "relation", "update", clitest.RelationID)
	if err != terminal.ErrNothingToUpdate {
		t.Fatalf("err = %v", err)
	}
	_, _, err = runCLI(t, `{"stge":"NEW"}`, "otak", "relation", "update", clitest.RelationID)
	if err == nil || err.Error() != `unknown field "stge". Did you mean "stage"?` {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestDeleteWithoutYesOnNonTerminal(t *testing.T) {
	api := relationFixture(t)
	_, _, err := runCLI(t, "y\n", "otak", "relation", "delete", clitest.RelationID)
	if err != terminal.ErrDeleteNeedsYes {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestDeleteWithYes(t *testing.T) {
	api := relationFixture(t)
	api.Respond("DELETE "+clitest.RelationPath, http.StatusOK, `{}`)
	out, _, err := runCLI(t, "", "otak", "relation", "delete", clitest.RelationID, "--yes")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Deleted relation "+clitest.RelationID) {
		t.Errorf("output = %q", out)
	}
}

func TestDeletePrompt(t *testing.T) {
	tests := []struct {
		answer  string
		deleted bool
	}{{"y\n", true}, {"YES\n", true}, {"n\n", false}, {"\n", false}, {"", false}, {"maybe\ny\n", true}, {"yep\nno\n", false}}
	for _, tt := range tests {
		t.Run(strings.TrimSpace(tt.answer), func(t *testing.T) {
			api := relationFixture(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, true)
			api.Respond("DELETE "+clitest.RelationPath, http.StatusOK, `{}`)
			out, _, err := runCLILines(t, tt.answer, "otak", "relation", "delete", clitest.RelationID)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(out, "Delete relation "+clitest.RelationID+"? (y/n)") {
				t.Errorf("no prompt in %q", out)
			}
			if got := len(api.Calls()) == 1; got != tt.deleted {
				t.Errorf("deleted = %v, want %v", got, tt.deleted)
			}
			if !tt.deleted && !strings.Contains(out, "Cancelled.") {
				t.Errorf("output = %q", out)
			}
		})
	}
}

func TestDeleteExitCodes(t *testing.T) {
	tests := []struct {
		status, code int
	}{{http.StatusNotFound, 2}, {http.StatusForbidden, 3}, {http.StatusInternalServerError, 1}}
	for _, tt := range tests {
		api := relationFixture(t)
		api.Respond("DELETE "+clitest.RelationPath, tt.status, `{}`)
		_, _, err := runCLI(t, "", "otak", "relation", "delete", clitest.RelationID, "-y")
		if err == nil || client.ExitCode(err) != tt.code {
			t.Errorf("status %d: err = %v, exit = %d, want %d", tt.status, err, client.ExitCode(err), tt.code)
		}
	}
}

func TestHelpWithAPI(t *testing.T) {
	relationFixture(t)
	out, _, err := runCLI(t, "", "otak", "relation", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"/v3/api-docs:", `"name": string,  (required)`, `"type": "LEAD" | "SUPPLIER" | "CUSTOMER",  (required)`,
		`"ownerUserId": string (uuid)`, `"expectedValue": number`, "oba otak relation create <<'EOF'", "< relation.json",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("create help lacks %q:\n%s", want, out)
		}
	}
	out, _, err = runCLI(t, "", "otak", "relation", "update", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "give only the ones to change.") || strings.Contains(out, "(required)") ||
		!strings.Contains(out, `"stage": "NEW"`) {
		t.Errorf("update help:\n%s", out)
	}
}

func TestHelpWithoutAPI(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /v3/api-docs", http.StatusInternalServerError, "down")
	t.Setenv("OBA_API_URL", api.URL())
	out, _, err := runCLI(t, "", "otak", "relation", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Fields unavailable: ") || !strings.Contains(out, "Creates one lead") {
		t.Errorf("help:\n%s", out)
	}
}

func TestMergeUpdateRefusesFieldTheDetailLacks(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+clitest.RelationPath, http.StatusOK,
		`{"data":{"id":"`+clitest.RelationID+`","name":"Acme","type":"LEAD","stage":null,"owner":null}}`)
	_, _, err := runCLI(t, `{"country":"DE"}`, "otak", "relation", "update", clitest.RelationID)
	if err == nil || !strings.Contains(err.Error(), `cannot keep field "size"`) {
		t.Fatalf("err = %v", err)
	}
	if got := strings.Join(api.Calls(), "|"); got != "GET "+clitest.RelationPath {
		t.Errorf("calls = %s", got)
	}
}
