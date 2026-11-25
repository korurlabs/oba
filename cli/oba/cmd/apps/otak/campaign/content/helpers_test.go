package content

import (
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	campaignID    = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
	contentID     = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
	legalEntityID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
	contentsPath  = "/otak/campaigns/" + campaignID + "/contents"
	contentPath   = contentsPath + "/" + contentID
)

// newRoot builds a fresh "oba otak campaign" tree with only this package's commands, as the
// campaign command adds them.
func newRoot() *cobra.Command {
	campaign := &cobra.Command{Use: "campaign"}
	campaign.AddCommand(NewCmd())
	return clitest.AppRoot("otak", campaign)
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

// newContentAPI starts a mock API serving the content request schema, signed in to
// clitest.OrgIDAcme, with no terminal on stdin.
func newContentAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.ServeDocs("testdata/api-docs-campaign-content.json")
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}
