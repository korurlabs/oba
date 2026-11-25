package cyberscore

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestCheckListWithStatus(t *testing.T) {
	api := newAPI(t)
	var query string
	api.Handle("GET "+checksURL, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, checksJSON)
	})
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "list", "--status", "in-progress")
	if err != nil {
		t.Fatal(err)
	}
	if query != "status=IN_PROGRESS" {
		t.Errorf("query = %q", query)
	}
	want := "ID                                    NAME            CATEGORY  WEIGHT  OUTCOME  STATUS\n" +
		idMFA + "  MFA enforced    IDENTITY  10      PASS     DONE\n" +
		idBackup + "  Backups tested  -         5       TODO     NOT_STARTED\n"
	if out != want {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
}

func TestCheckListEmptyAndBadStatus(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+checksURL, http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "list")
	if err != nil || out != "No checks found.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	calls := len(api.Calls())
	if _, _, err := runCLI(t, "", "kazan", "cyberscore", "check", "list", "--status", "open"); err == nil ||
		!strings.Contains(err.Error(), `invalid status "open"`) || len(api.Calls()) != calls {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

// A seed script reads the names of the existing checks from the --json list.
func TestCheckListJSONIsTheArray(t *testing.T) {
	newAPI(t)
	out, err := runJSON(t, "kazan", "cyberscore", "check", "list")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(strings.TrimSpace(out), "[") || !strings.Contains(out, `"name": "Backups tested"`) {
		t.Errorf("out = %s", out)
	}
}
