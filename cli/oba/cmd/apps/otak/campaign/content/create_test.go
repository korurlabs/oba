package content

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const createdJSON = `{"data":{"id":"` + contentID + `","campaignId":"` + campaignID + `","legalEntityId":"` + legalEntityID +
	`","channel":"LINKEDIN","language":"NL","text":"Hallo","status":"DRAFT"}}`

func TestContentCreateSendsTheObjectUnchanged(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("POST "+contentsPath, http.StatusCreated, createdJSON)
	in := `{"legalEntityId": "` + legalEntityID + `", "channel": "LINKEDIN", "language": "NL", "text": "Hallo é"}`
	out, _, err := runCLI(t, in, "otak", "campaign", "content", "create", campaignID)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created content "+contentID+" (LINKEDIN, NL, DRAFT)\n" {
		t.Errorf("out = %q", out)
	}
	want := `{"legalEntityId":"` + legalEntityID + `","channel":"LINKEDIN","language":"NL","text":"Hallo é"}`
	if got := api.Request("POST " + contentsPath).Body; got != want {
		t.Errorf("body = %s", got)
	}
}

func TestContentCreateLegalEntityFlagReplacesTheField(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("POST "+contentsPath, http.StatusCreated, createdJSON)
	in := `{"legalEntityId": "other", "channel": "LINKEDIN", "language": "NL", "text": "Hallo"}`
	if _, _, err := runCLI(t, in, "otak", "campaign", "content", "create", campaignID, "--legal-entity", legalEntityID); err != nil {
		t.Fatal(err)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(api.Request("POST "+contentsPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	if body["legalEntityId"] != legalEntityID || body["text"] != "Hallo" || len(body) != 4 {
		t.Errorf("body = %v", body)
	}

	api = newContentAPI(t)
	api.Respond("POST "+contentsPath, http.StatusCreated, createdJSON)
	in = `{"channel": "X", "language": "EN", "text": "Hi"}`
	if _, _, err := runCLI(t, in, "otak", "campaign", "content", "create", campaignID, "--legal-entity", legalEntityID); err != nil {
		t.Fatal(err)
	}
	if got := api.Request("POST " + contentsPath).Body; !strings.Contains(got, `"legalEntityId":"`+legalEntityID+`"`) {
		t.Errorf("body = %s", got)
	}
}

func TestContentCreateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"channel":"X","plannedAtt":"x"}`, `unknown field "plannedAtt". Did you mean "plannedAt"?`, false},
		{"title", `{"channel":"X","title":"x"}`, `unknown field "title"`, false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba otak campaign content create --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newContentAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "otak", "campaign", "content", "create", campaignID)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestContentCreateLegalEntityOfAnotherOrganization(t *testing.T) {
	api := newContentAPI(t)
	api.Respond("POST "+contentsPath, http.StatusNotFound, `{"error":{"code":"LGE_NOT_001","message":"Legal entity not found"}}`)
	in := `{"channel":"X","language":"EN","text":"Hi"}`
	_, _, err := runCLI(t, in, "otak", "campaign", "content", "create", campaignID, "--legal-entity", legalEntityID)
	if err == nil || err.Error() != "LGE_NOT_001: Legal entity not found" || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestContentHelpListsLiveFields(t *testing.T) {
	newContentAPI(t)
	out, _, err := runCLI(t, "", "otak", "campaign", "content", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, `"channel": "LINKEDIN" | "X" | "NEWSLETTER"`) || !strings.Contains(out, "(required)") ||
		!strings.Contains(out, "--legal-entity") {
		t.Errorf("out = %s", out)
	}
	out, _, err = runCLI(t, "", "otak", "campaign", "content", "update", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "give only the ones to change.") || strings.Contains(out, "(required)") {
		t.Errorf("out = %s", out)
	}
}
