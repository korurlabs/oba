package contact

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const (
	contactID    = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
	contactsPath = clitest.RelationPath + "/contacts"
	contactPath  = contactsPath + "/" + contactID
)

// contactJSON is a person contact, not main, as the API returns it.
func contactJSON(name string) string {
	return `{"id":"` + contactID + `","relationId":"` + clitest.RelationID + `","name":"` + name + `","kind":"PERSON","role":"Director",` +
		`"email":"jan@acme.nl","emailType":"COMPANY","phone":null,"linkedinUrl":null,"decisionMaker":false,` +
		`"mainPersonContact":false,"mainBusinessContact":false,"sourceUrl":"https://acme.nl/team"}`
}

// mainPersonJSON is contactJSON as the relation's main person contact.
func mainPersonJSON(name string) string {
	return strings.Replace(contactJSON(name), `"mainPersonContact":false`, `"mainPersonContact":true`, 1)
}

// mainBusinessJSON is contactJSON as the relation's main business contact.
func mainBusinessJSON(name string) string {
	business := strings.Replace(contactJSON(name), `"kind":"PERSON"`, `"kind":"BUSINESS"`, 1)
	return strings.Replace(business, `"mainBusinessContact":false`, `"mainBusinessContact":true`, 1)
}

func TestContactListTable(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+contactJSON("Jan\\u001b[31m de\\tVries")+`]}`)
	out, _, err := runCLI(t, "", "otak", "contact", "list", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	if strings.ContainsRune(out, 0x1b) || strings.Contains(out, "\t") {
		t.Errorf("control characters in %q", out)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "NAME") || !strings.Contains(lines[0], "EMAIL TYPE") ||
		strings.Fields(lines[0])[1] != "KIND" || strings.Fields(lines[0])[2] != "MAIN" {
		t.Fatalf("out =\n%s", out)
	}
	for _, want := range []string{" person ", "Director", "jan@acme.nl", "COMPANY", "https://acme.nl/team", contactID, " - "} {
		if !strings.Contains(lines[1], want) {
			t.Errorf("row lacks %q: %q", want, lines[1])
		}
	}
}

func TestContactListKindAndMainColumns(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+mainPersonJSON("Ann")+`,`+mainBusinessJSON("Info")+`,`+contactJSON("Jan")+`]}`)
	out, _, err := runCLI(t, "", "otak", "contact", "list", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 4 {
		t.Fatalf("out =\n%s", out)
	}
	want := [][]string{{"Ann", "person", "yes"}, {"Info", "business", "yes"}, {"Jan", "person", "-"}}
	for i, row := range want {
		got := strings.Fields(lines[i+1])
		if got[0] != row[0] || got[1] != row[1] || got[2] != row[2] {
			t.Errorf("row %d = %q, want %v", i+1, lines[i+1], row)
		}
	}
}

func TestContactUpdateMainPersonContactKeepsOtherFields(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+contactJSON("Jan")+`]}`)
	api.Respond("PUT "+contactPath, http.StatusOK, `{"data":`+contactJSON("Jan")+`}`)
	out, _, err := runCLI(t, `{"mainPersonContact": true}`, "otak", "contact", "update", clitest.RelationID, contactID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Updated contact "+contactID) {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(api.Calls(), ","); got != "GET "+contactsPath+",PUT "+contactPath {
		t.Errorf("calls = %s", got)
	}
	body := api.Request("PUT " + contactPath).Body
	for _, want := range []string{`"mainPersonContact":true`, `"mainBusinessContact":false`, `"kind":"PERSON"`, `"name":"Jan"`, `"role":"Director"`, `"email":"jan@acme.nl"`,
		`"emailType":"COMPANY"`, `"phone":null`, `"linkedinUrl":null`, `"decisionMaker":false`} {
		if !strings.Contains(body, want) {
			t.Errorf("body lacks %s: %s", want, body)
		}
	}
	if strings.Contains(body, "sourceUrl") {
		t.Errorf("body carries sourceUrl: %s", body)
	}
}

func TestContactUpdateSendsStoredMainFlag(t *testing.T) {
	api := relationFixture(t)
	main := mainPersonJSON("Jan")
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+main+`]}`)
	api.Respond("PUT "+contactPath, http.StatusOK, `{"data":`+main+`}`)
	if _, _, err := runCLI(t, `{"phone":"+31 10 123456"}`, "otak", "contact", "update", clitest.RelationID, contactID); err != nil {
		t.Fatal(err)
	}
	if body := api.Request("PUT " + contactPath).Body; !strings.Contains(body, `"mainPersonContact":true`) {
		t.Errorf("body = %s", body)
	}
}

// The API refuses false on the main contact of an unchanged kind; the CLI sends it as given, so
// that the API's answer reaches the user.
func TestContactUpdateFalseOnMainPersonIsSentAsGiven(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+mainPersonJSON("Jan")+`]}`)
	api.Respond("PUT "+contactPath, http.StatusUnprocessableEntity,
		`{"error":{"code":"GEN_VAL_001","message":"Validation failed","errorValues":{"mainPersonContact":"Mark another contact as main instead"}}}`)
	_, _, err := runCLI(t, `{"mainPersonContact": false}`, "otak", "contact", "update", clitest.RelationID, contactID)
	if err == nil || !strings.Contains(err.Error(), "GEN_VAL_001") || !strings.Contains(err.Error(), "Mark another contact as main instead") {
		t.Fatalf("err = %v", err)
	}
	body := api.Request("PUT " + contactPath).Body
	for _, want := range []string{`"kind":"PERSON"`, `"decisionMaker":false`, `"mainPersonContact":false`, `"mainBusinessContact":false`} {
		if !strings.Contains(body, want) {
			t.Errorf("body lacks %s: %s", want, body)
		}
	}
}

func TestContactUpdateKindChangeClearsFlagOfOldKind(t *testing.T) {
	tests := []struct {
		name, stored, in string
		want             []string
	}{
		{"main person to business", mainPersonJSON("Jan"), `{"kind":"BUSINESS"}`,
			[]string{`"kind":"BUSINESS"`, `"mainPersonContact":false`, `"mainBusinessContact":false`}},
		{"main business to person", mainBusinessJSON("Info"), `{"kind":"PERSON"}`,
			[]string{`"kind":"PERSON"`, `"mainPersonContact":false`, `"mainBusinessContact":false`}},
		{"given flags sent as given", mainPersonJSON("Jan"), `{"kind":"BUSINESS","mainPersonContact":true,"mainBusinessContact":true}`,
			[]string{`"kind":"BUSINESS"`, `"mainPersonContact":true`, `"mainBusinessContact":true`}},
		{"same kind keeps the flag", mainPersonJSON("Jan"), `{"kind":"PERSON","role":"CEO"}`,
			[]string{`"kind":"PERSON"`, `"mainPersonContact":true`}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := relationFixture(t)
			api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+tt.stored+`]}`)
			api.Respond("PUT "+contactPath, http.StatusOK, `{"data":`+contactJSON("Jan")+`}`)
			if _, _, err := runCLI(t, tt.in, "otak", "contact", "update", clitest.RelationID, contactID); err != nil {
				t.Fatal(err)
			}
			body := api.Request("PUT " + contactPath).Body
			for _, want := range tt.want {
				if !strings.Contains(body, want) {
					t.Errorf("body lacks %s: %s", want, body)
				}
			}
		})
	}
}

func TestContactListEmpty(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "otak", "contact", "list", clitest.RelationID)
	if err != nil || out != "No contacts.\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestContactCreateSendsStdinUnchanged(t *testing.T) {
	api := relationFixture(t)
	api.Respond("POST "+contactsPath, http.StatusCreated, `{"data":`+contactJSON("Jan")+`}`)
	in := `{"name": "Jan",  "sourceUrl": "https://acme.nl/team", "decisionMaker": true}`
	out, _, err := runCLI(t, in, "otak", "contact", "create", clitest.RelationID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Created contact "+contactID) {
		t.Errorf("out = %q", out)
	}
	if body := api.Request("POST " + contactsPath).Body; !strings.Contains(body, `"decisionMaker":true`) || !strings.Contains(body, `"sourceUrl":"https://acme.nl/team"`) {
		t.Errorf("body = %s", body)
	}
}

func TestContactCreateUnknownKeyBeforeAnyCall(t *testing.T) {
	api := relationFixture(t)
	_, _, err := runCLI(t, `{"name":"Jan","rol":"x"}`, "otak", "contact", "create", clitest.RelationID)
	if err == nil || !strings.Contains(err.Error(), `unknown field "rol". Did you mean "role"?`) {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestContactUpdateMergesAndKeepsSourceOut(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+contactJSON("Jan")+`]}`)
	api.Respond("PUT "+contactPath, http.StatusOK, `{"data":`+contactJSON("Jan")+`}`)
	out, _, err := runCLI(t, `{"phone":"+31 10 123456","role":null}`, "otak", "contact", "update", clitest.RelationID, contactID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Updated contact "+contactID) {
		t.Errorf("out = %q", out)
	}
	body := api.Request("PUT " + contactPath).Body
	for _, want := range []string{`"name":"Jan"`, `"email":"jan@acme.nl"`, `"phone":"+31 10 123456"`, `"role":null`, `"decisionMaker":false`, `"linkedinUrl":null`} {
		if !strings.Contains(body, want) {
			t.Errorf("body lacks %s: %s", want, body)
		}
	}
	if strings.Contains(body, "sourceUrl") {
		t.Errorf("body carries sourceUrl: %s", body)
	}
}

func TestContactUpdateRefusals(t *testing.T) {
	tests := []struct{ name, in, want string }{
		{"source url", `{"sourceUrl":"https://x.nl"}`, "sourceUrl is set on create only"},
		{"empty object", `{}`, "Nothing to update."},
		{"unknown key", `{"colour":"red"}`, `unknown field "colour"`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := relationFixture(t)
			_, _, err := runCLI(t, tt.in, "otak", "contact", "update", clitest.RelationID, contactID)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestContactUpdateNothingChangedSendsNoPut(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+contactJSON("Jan")+`]}`)
	out, _, err := runCLI(t, `{ "role" : "Director", "phone": null }`, "otak", "contact", "update", clitest.RelationID, contactID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Nothing to update: contact "+contactID+" already has these values.") {
		t.Errorf("out = %q", out)
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

// With --json, an update that sends nothing gives the contact, not the relation's contact list
// it was read from.
func TestContactUpdateNothingChangedJSONIsTheContact(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[`+contactJSON("Jan")+`]}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)

	if _, _, err := runCLI(t, `{"role":"Director"}`, "otak", "contact", "update", clitest.RelationID, contactID); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	var got map[string]json.RawMessage
	if err := json.Unmarshal(buf.Bytes(), &got); err != nil {
		t.Fatalf("result is not a JSON object: %v\n%s", err, buf.String())
	}
	if string(got["id"]) != `"`+contactID+`"` || string(got["sourceUrl"]) != `"https://acme.nl/team"` {
		t.Errorf("result = %s", buf.String())
	}
}

func TestContactUpdateMissingContact(t *testing.T) {
	api := relationFixture(t)
	api.Respond("GET "+contactsPath, http.StatusOK, `{"data":[]}`)
	_, _, err := runCLI(t, `{"role":"CEO"}`, "otak", "contact", "update", clitest.RelationID, contactID)
	if err == nil || !strings.Contains(err.Error(), "not found") || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestContactDeleteNeedsYesWithoutTerminal(t *testing.T) {
	api := relationFixture(t)
	_, _, err := runCLI(t, "y\n", "otak", "contact", "delete", clitest.RelationID, contactID)
	if err != terminal.ErrDeleteNeedsYes {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestContactDeleteWithYes(t *testing.T) {
	api := relationFixture(t)
	api.Respond("DELETE "+contactPath, http.StatusOK, `{}`)
	out, _, err := runCLI(t, "", "otak", "contact", "delete", clitest.RelationID, contactID, "--yes")
	if err != nil || !strings.Contains(out, "Deleted contact "+contactID) {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestContactDeletePrompt(t *testing.T) {
	tests := []struct {
		answer  string
		deleted bool
	}{{"y\n", true}, {"n\n", false}}
	for _, tt := range tests {
		t.Run(strings.TrimSpace(tt.answer), func(t *testing.T) {
			api := relationFixture(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, true)
			api.Respond("DELETE "+contactPath, http.StatusOK, `{}`)
			out, _, err := runCLILines(t, tt.answer, "otak", "contact", "delete", clitest.RelationID, contactID)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(out, "Delete contact "+contactID+"? (y/n)") {
				t.Errorf("no prompt in %q", out)
			}
			if got := len(api.Calls()) == 1; got != tt.deleted {
				t.Errorf("deleted = %v, want %v", got, tt.deleted)
			}
			if !tt.deleted && !strings.Contains(out, "Cancelled.") {
				t.Errorf("out = %q", out)
			}
		})
	}
}

func TestContactAPIErrorsSetExitCode(t *testing.T) {
	tests := []struct {
		status, code int
	}{{http.StatusNotFound, 2}, {http.StatusForbidden, 3}}
	for _, tt := range tests {
		api := relationFixture(t)
		api.Respond("DELETE "+contactPath, tt.status, `{"message":"nope"}`)
		_, _, err := runCLI(t, "", "otak", "contact", "delete", clitest.RelationID, contactID, "-y")
		if err == nil || client.ExitCode(err) != tt.code || !strings.Contains(err.Error(), "nope") {
			t.Errorf("status %d: err = %v, exit = %d", tt.status, err, client.ExitCode(err))
		}
		api.Respond("GET "+contactsPath, tt.status, `{"message":"nope"}`)
		_, _, err = runCLI(t, "", "otak", "contact", "list", clitest.RelationID)
		if err == nil || client.ExitCode(err) != tt.code {
			t.Errorf("list status %d: err = %v", tt.status, err)
		}
	}
}

func TestContactHelpListsFieldsWithoutSourceOnUpdate(t *testing.T) {
	relationFixture(t)
	create, _, err := runCLI(t, "", "otak", "contact", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	update, _, err := runCLI(t, "", "otak", "contact", "update", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(create, "linkedinUrl") || !strings.Contains(create, "Fields, from ") {
		t.Errorf("create help lacks the fields:\n%s", create)
	}
	if !strings.Contains(update, "linkedinUrl") || strings.Contains(update, "\n  sourceUrl") {
		t.Errorf("update help fields wrong:\n%s", update)
	}
}
