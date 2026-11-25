package cyberscore

import (
	"bytes"
	"net/http"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	idMFA     = "c0000000-0000-4000-8000-000000000001"
	idBackup  = "c0000000-0000-4000-8000-000000000002"
	checksURL = "/kazan/cyberscore/checks"
	mfaJSON   = `{"id":"` + idMFA + `","name":"MFA enforced","description":"Why","expected":"Every account signs in with MFA",` +
		`"weight":10,"sortOrder":1,"category":"IDENTITY","outcome":"PASS","status":"DONE","updatedAt":"2026-11-25T10:00:00Z"}`
	backupJSON = `{"id":"` + idBackup + `","name":"Backups tested","description":null,"expected":"A restore ran this quarter",` +
		`"weight":5,"sortOrder":0,"category":null,"outcome":"TODO","status":"NOT_STARTED","updatedAt":"2026-11-25T10:00:00Z"}`
	checksJSON = `{"data":[` + mfaJSON + `,` + backupJSON + `]}`
)

// newRoot builds oba kazan with the persistent --board flag the real group has, so that a test
// proves the cyberscore commands never need it.
func newRoot() *cobra.Command {
	root := clitest.AppRoot("kazan", NewCmd())
	group, _, _ := root.Find([]string{"kazan"})
	boardflag.Add(group)
	return root
}

func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// runJSON runs the command with --json output and returns what it would print.
func runJSON(t *testing.T, args ...string) (string, error) {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", args...); err != nil {
		return "", err
	}
	if err := jsonout.Flush(); err != nil {
		return "", err
	}
	return buf.String(), nil
}

// newAPI starts a mock API serving the two checks, signed in to Acme with no default board, and
// with stdin not a terminal.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET "+checksURL, http.StatusOK, checksJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

func checkRoute(method, id, suffix string) string {
	return method + " " + checksURL + "/" + id + suffix
}
