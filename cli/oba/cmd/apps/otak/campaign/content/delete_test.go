package content

import (
	"errors"
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestContentDelete(t *testing.T) {
	tests := []struct {
		name, stdin string
		args        []string
		terminal    bool
		wantDelete  bool
		wantOut     string
		wantErr     error
	}{
		{"--yes", "", []string{"--yes"}, false, true, "Deleted content " + contentID + "\n", nil},
		{"confirmed", "y\n", nil, true, true, "Delete content " + contentID + "? (y/n) Deleted content " + contentID + "\n", nil},
		{"declined", "n\n", nil, true, false, "Delete content " + contentID + "? (y/n) Cancelled.\n", nil},
		{"no terminal", "", nil, false, false, "", terminal.ErrDeleteNeedsYes},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newContentAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			api.Respond("DELETE "+contentPath, http.StatusOK, `{"message":"ok"}`)
			args := append([]string{"otak", "campaign", "content", "delete", campaignID, contentID}, tt.args...)
			out, _, err := runCLILines(t, tt.stdin, args...)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if out != tt.wantOut {
				t.Errorf("out = %q", out)
			}
			deleted := len(api.Calls()) == 1 && api.Calls()[0] == "DELETE "+contentPath
			if deleted != tt.wantDelete {
				t.Errorf("calls = %v", api.Calls())
			}
		})
	}
}
