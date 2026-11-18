package org

import (
	"net/http"
	"strings"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/shrine"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	newOrgID       = "44444444-4444-4444-8444-444444444444"
	orgCreatedJSON = `{"data":{"id":"` + newOrgID + `","name":"Korur B.V.","country":"NLD"}}`
)

func TestOrgCreateSendsBodyWithoutOrganization(t *testing.T) {
	api := newOrgAPI(t, "")
	api.Respond("POST /organizations", http.StatusCreated, orgCreatedJSON)
	in := `{"name": "Korur B.V.",  "country": "NLD", "website": null}`
	out, _, err := runCLI(t, in, "admin", "org", "create")
	if err != nil {
		t.Fatal(err)
	}
	want := "Created organization Korur B.V. (" + newOrgID + "). You are its owner; run 'oba org use " + newOrgID + "' to work in it.\n"
	if out != want {
		t.Errorf("out = %q", out)
	}
	req := api.Request("POST /organizations")
	if req.Body != `{"name":"Korur B.V.","country":"NLD","website":null}` {
		t.Errorf("body = %s", req.Body)
	}
	if req.Org != "" {
		t.Errorf("organization header %q sent", req.Org)
	}
}

func TestOrgCreateWithCurrentOrganizationSendsNoHeader(t *testing.T) {
	api := newOrgAPI(t, clitest.OrgIDAcme)
	api.Respond("POST /organizations", http.StatusCreated, orgCreatedJSON)
	if _, _, err := runCLI(t, `{"name":"Korur B.V."}`, "admin", "org", "create"); err != nil {
		t.Fatal(err)
	}
	if got := api.Request("POST /organizations").Org; got != "" {
		t.Errorf("organization header %q sent", got)
	}
}

func TestOrgCreateSanitizesMessage(t *testing.T) {
	api := newOrgAPI(t, "")
	api.Respond("POST /organizations", http.StatusCreated,
		`{"data":{"id":"`+newOrgID+`","name":"Evil\u001b[31m\nCorp"}}`)
	out, _, err := runCLI(t, `{"name":"x"}`, "admin", "org", "create")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(out, "Created organization Evil Corp (") || strings.ContainsRune(out, 0x1b) || strings.Count(out, "\n") != 1 {
		t.Errorf("out = %q", out)
	}
}

func TestOrgCreateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"name":"x","contry":"NLD"}`, `unknown field "contry". Did you mean "country"?`, false},
		{"not an object", `[1]`, "not an array", false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba admin org create --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newOrgAPI(t, "")
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "admin", "org", "create")
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			for _, call := range api.Calls() {
				if strings.HasPrefix(call, "POST ") {
					t.Errorf("calls = %v", api.Calls())
				}
			}
		})
	}
}

func TestOrgCreateValidationError(t *testing.T) {
	api := newOrgAPI(t, "")
	api.Respond("POST /organizations", http.StatusUnprocessableEntity,
		`{"error":{"code":"GEN_VAL_001","message":"Validation failed","errorValues":{"country":"Country code must be at most 3 characters"}}}`)
	_, _, err := runCLI(t, `{"name":"Korur B.V.","country":"NLDX"}`, "admin", "org", "create")
	want := "GEN_VAL_001: Validation failed\n  country: Country code must be at most 3 characters"
	if err == nil || err.Error() != want || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestOrgCreateHelpListsLiveFields(t *testing.T) {
	newOrgAPI(t, "")
	out, _, err := runCLI(t, "", "admin", "org", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, `"name": string,  (required)`) || !strings.Contains(out, `"country"`) || strings.Count(out, "(required)") != 1 {
		t.Errorf("out = %s", out)
	}
}

func TestOrgListMatchesOrgList(t *testing.T) {
	api := newOrgAPI(t, clitest.OrgIDAcme)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(shrine.Commands()...)
	want, _, err := clitest.RunCLI(t, root, "", "org", "list")
	if err != nil {
		t.Fatal(err)
	}
	out, _, err := runCLI(t, "", "admin", "org", "list")
	if err != nil {
		t.Fatal(err)
	}
	if out != want || !strings.Contains(out, "*  Acme") {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
	if got := api.Request("GET /users/me").Org; got != "" {
		t.Errorf("/users/me sent organization header %q", got)
	}
}

func TestOrgListEmpty(t *testing.T) {
	api := newOrgAPI(t, "")
	api.Respond("GET /users/me", http.StatusOK, `{"data":{"id":"u1","email":"a@b.c","organizations":[]}}`)
	out, _, err := runCLI(t, "", "admin", "org", "list")
	if err != nil {
		t.Fatal(err)
	}
	if out != "You belong to no organization.\n" {
		t.Errorf("out = %q", out)
	}
}

const (
	orgPath = "/organizations/" + clitest.OrgIDAcme

	// orgReadJSON is GET /organizations/{id} of Acme; the description carries an escape and two
	// spaces, so that a byte-for-byte write back shows.
	orgReadJSON = `{"data":{"id":"` + clitest.OrgIDAcme + `","createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z",` +
		`"name":"Acme","description":"Caf\u00e9  security","industry":"Security","companySize":"11-50","country":"NLD",` +
		`"website":"https://acme.nl/","status":"ACTIVE"}}`
)

func TestOrgUpdateMergesGivenKeysOverCurrent(t *testing.T) {
	api := newOrgAPI(t, clitest.OrgIDAcme)
	api.Respond("GET "+orgPath, http.StatusOK, orgReadJSON)
	api.Respond("PUT "+orgPath, http.StatusOK, `{"data":{"id":"`+clitest.OrgIDAcme+`","name":"Acme Security"}}`)
	out, _, err := runCLI(t, `{"name": "Acme Security", "website": null}`, "admin", "org", "update")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated organization Acme Security ("+clitest.OrgIDAcme+")\n" {
		t.Errorf("out = %q", out)
	}
	if got := api.Calls(); len(got) != 2 || got[0] != "GET "+orgPath || got[1] != "PUT "+orgPath {
		t.Errorf("calls = %v", got)
	}
	want := `{"companySize":"11-50","country":"NLD","description":"Caf\u00e9  security","industry":"Security","name":"Acme Security","website":null}`
	put := api.Request("PUT " + orgPath)
	if put.Body != want {
		t.Errorf("body = %s\nwant   %s", put.Body, want)
	}
	if put.Org != clitest.OrgIDAcme || api.Request("GET "+orgPath).Org != clitest.OrgIDAcme {
		t.Errorf("organization header = %q", put.Org)
	}
}

func TestOrgUpdateNothingChangedSendsNoPut(t *testing.T) {
	api := newOrgAPI(t, clitest.OrgIDAcme)
	api.Respond("GET "+orgPath, http.StatusOK, orgReadJSON)
	out, _, err := runCLI(t, `{ "industry" : "Security", "country": "NLD" }`, "admin", "org", "update")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Nothing to update: Acme already has these values.\n" {
		t.Errorf("out = %q", out)
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

func TestOrgUpdateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"empty object", `{}`, "Nothing to update.", false},
		{"unknown key", `{"webiste":null}`, `unknown field "webiste". Did you mean "website"?`, false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba admin org update --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newOrgAPI(t, clitest.OrgIDAcme)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "admin", "org", "update")
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestOrgUpdateNoOrganization(t *testing.T) {
	api := newOrgAPI(t, "")
	_, _, err := runCLI(t, `{"industry":"Security"}`, "admin", "org", "update")
	if err == nil || !strings.Contains(err.Error(), "No organization selected") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestOrgUpdateForbiddenOnRead(t *testing.T) {
	api := newOrgAPI(t, clitest.OrgIDAcme)
	api.Respond("GET "+orgPath, http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient organization role"}}`)
	_, _, err := runCLI(t, `{"industry":"Security"}`, "admin", "org", "update")
	if err == nil || !strings.Contains(err.Error(), "ORG_SEC_003") || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

func TestOrgUpdateValidationError(t *testing.T) {
	api := newOrgAPI(t, clitest.OrgIDAcme)
	api.Respond("GET "+orgPath, http.StatusOK, orgReadJSON)
	api.Respond("PUT "+orgPath, http.StatusUnprocessableEntity,
		`{"error":{"code":"GEN_VAL_001","message":"Validation failed","errorValues":{"name":"Organization name is required"}}}`)
	_, _, err := runCLI(t, `{"name":null}`, "admin", "org", "update")
	want := "GEN_VAL_001: Validation failed\n  name: Organization name is required"
	if err == nil || err.Error() != want || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
	if body := api.Request("PUT " + orgPath).Body; !strings.Contains(body, `"name":null`) {
		t.Errorf("body = %s", body)
	}
}

func TestOrgUpdateHelpListsUpdateFields(t *testing.T) {
	newOrgAPI(t, clitest.OrgIDAcme)
	out, _, err := runCLI(t, "", "admin", "org", "update", "--help")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"give only the ones to change.", `"website"`, `"companySize": string | null`} {
		if !strings.Contains(out, want) {
			t.Errorf("help lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(out, "(required)") {
		t.Errorf("update help marks a field required:\n%s", out)
	}
}

func TestOrgHelpNamesWhatIsLeftOut(t *testing.T) {
	out, _, err := runCLI(t, "", "admin", "org", "--help")
	if err != nil {
		t.Fatal(err)
	}
	flat := strings.Join(strings.Fields(out), " ")
	for _, want := range []string{"Deleting, disabling and enabling an organization are left out of this CLI on purpose", "Oba admin web app", "update"} {
		if !strings.Contains(flat, want) {
			t.Errorf("help lacks %q:\n%s", want, out)
		}
	}
}
