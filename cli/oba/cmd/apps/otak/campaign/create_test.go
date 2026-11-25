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
		`{"data":{"id":"`+campaignID+`","name":"De wacht","contentCount":0}}`)
	in := `{"name": "De wacht", "description": "Nachtwacht é", "startDate": "2027-03-01", "endDate": "2027-03-31"}`
	out, _, err := runCLI(t, in, "otak", "campaign", "create")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created campaign "+campaignID+" (De wacht)\n" {
		t.Errorf("out = %q", out)
	}
	want := `{"name":"De wacht","description":"Nachtwacht é","startDate":"2027-03-01","endDate":"2027-03-31"}`
	if got := api.Request("POST /otak/campaigns").Body; got != want {
		t.Errorf("body = %s", got)
	}
}

func TestCampaignCreateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"name":"x","startDat":"2027-03-01"}`, `unknown field "startDat". Did you mean "startDate"?`, false},
		{"removed field", `{"name":"x","channel":"X"}`, `unknown field "channel"`, false},
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

func TestCampaignCreateDateRange(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("POST /otak/campaigns", http.StatusUnprocessableEntity,
		`{"error":{"code":"OTK_VAL_017","message":"The end date is before the start date"}}`)
	_, _, err := runCLI(t, `{"name":"x","startDate":"2027-03-31","endDate":"2027-03-01"}`, "otak", "campaign", "create")
	if err == nil || err.Error() != "OTK_VAL_017: The end date is before the start date" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestCampaignHelpListsLiveFields(t *testing.T) {
	newCampaignAPI(t)
	out, _, err := runCLI(t, "", "otak", "campaign", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, `"startDate"`) || !strings.Contains(out, "(required)") {
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
