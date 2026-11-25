package campaign

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	campaignID   = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
	campaignPath = "/otak/campaigns/" + campaignID
)

func TestCampaignCreateSendsTheObjectUnchanged(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("POST /otak/campaigns", http.StatusCreated,
		`{"data":{"id":"`+campaignID+`","name":"Launch","channel":"LINKEDIN","status":"DRAFT","contents":[]}}`)
	in := `{"name": "Launch", "channel": "LINKEDIN", "contents": [{"language": "NL", "text": "Hallo é"}]}`
	out, _, err := runCLI(t, in, "otak", "campaign", "create")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created campaign "+campaignID+" (Launch, LINKEDIN, DRAFT)\n" {
		t.Errorf("out = %q", out)
	}
	want := `{"name":"Launch","channel":"LINKEDIN","contents":[{"language":"NL","text":"Hallo é"}]}`
	if got := api.Request("POST /otak/campaigns").Body; got != want {
		t.Errorf("body = %s", got)
	}
}

func TestCampaignCreateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"name":"x","channel":"X","plannedAtt":"x"}`, `unknown field "plannedAtt". Did you mean "plannedAt"?`, false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba otak campaign create --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newCampaignAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "otak", "campaign", "create")
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestCampaignCreateStatusDates(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("POST /otak/campaigns", http.StatusUnprocessableEntity,
		`{"error":{"code":"OTK_VAL_016","message":"A scheduled campaign needs a planned date"}}`)
	_, _, err := runCLI(t, `{"name":"Launch","channel":"X","status":"SCHEDULED"}`, "otak", "campaign", "create")
	if err == nil || err.Error() != "OTK_VAL_016: A scheduled campaign needs a planned date" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestCampaignHelpListsLiveFields(t *testing.T) {
	newCampaignAPI(t)
	out, _, err := runCLI(t, "", "otak", "campaign", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, `"channel": "LINKEDIN" | "X" | "NEWSLETTER"`) || !strings.Contains(out, "(required)") {
		t.Errorf("out = %s", out)
	}
	out, _, err = runCLI(t, "", "otak", "campaign", "update", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "give only the ones to change.") || strings.Contains(out, "(required)") {
		t.Errorf("out = %s", out)
	}
}
