package campaign

import (
	"errors"
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestCampaignDelete(t *testing.T) {
	tests := []struct {
		name, stdin string
		args        []string
		terminal    bool
		wantDelete  bool
		wantOut     string
		wantErr     error
	}{
		{"--yes", "", []string{"--yes"}, false, true, "Deleted campaign " + campaignID + "\n", nil},
		{"confirmed", "y\n", nil, true, true, "Delete campaign " + campaignID + "? (y/n) Deleted campaign " + campaignID + "\n", nil},
		{"declined", "n\n", nil, true, false, "Delete campaign " + campaignID + "? (y/n) Cancelled.\n", nil},
		{"no terminal", "", nil, false, false, "", terminal.ErrDeleteNeedsYes},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newCampaignAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			api.Respond("DELETE "+campaignPath, http.StatusOK, `{"message":"ok"}`)
			out, _, err := runCLILines(t, tt.stdin, append([]string{"otak", "campaign", "delete", campaignID}, tt.args...)...)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if out != tt.wantOut {
				t.Errorf("out = %q", out)
			}
			deleted := len(api.Calls()) == 1 && api.Calls()[0] == "DELETE "+campaignPath
			if deleted != tt.wantDelete {
				t.Errorf("calls = %v", api.Calls())
			}
		})
	}
}

func TestCampaignDeleteForbidden(t *testing.T) {
	api := newCampaignAPI(t)
	api.Respond("DELETE "+campaignPath, http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)
	_, _, err := runCLI(t, "", "otak", "campaign", "delete", campaignID, "--yes")
	if err == nil || err.Error() != "ORG_SEC_003: Insufficient role" || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}
