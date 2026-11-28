package campaign

import (
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// newRoot builds a fresh "oba otak" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("otak", NewCmd())
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

// newCampaignAPI starts a mock API serving the campaign request schemas, signed in to
// clitest.OrgIDAcme, with no terminal on stdin.
func newCampaignAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.ServeDocs("testdata/api-docs-campaign.json")
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}
