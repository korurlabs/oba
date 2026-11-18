package invite

import (
	"net/http"
	"testing"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// newRoot builds a fresh "oba admin" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("admin", NewCmd())
}

// runCLI runs "oba <args>" in process on a fresh tree; see clitest.RunCLI.
func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// runCLILines runs "oba <args>" like runCLI, with stdin delivered one line per read; see
// clitest.RunCLILines.
func runCLILines(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, args...)
}

// newInviteAPI starts a mock API serving /users/me, signed in to clitest.OrgIDAcme, with no
// terminal on stdin and the local time zone fixed to Istanbul (UTC+3), so that a day shift
// between UTC and the local day shows.
func newInviteAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	old := localZone
	localZone = func() *time.Location { return time.FixedZone("TRT", 3*60*60) }
	t.Cleanup(func() { localZone = old })
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}
