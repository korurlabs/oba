package board

import (
	"net/http"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	idOps   = "0a000000-0000-4000-8000-000000000001"
	idInfra = "0a000000-0000-4000-8000-000000000002"
	idKagi  = "0a000000-0000-4000-8000-000000000003"

	treeJSON = `{"data":[` +
		`{"id":"` + idOps + `","name":"Operations","description":"Day to day work","prefix":"OPS","slug":"operations","childBoards":[` +
		`{"id":"` + idInfra + `","name":"Infra","description":"","prefix":"INF","slug":"infra","childBoards":[]}]},` +
		`{"id":"` + idKagi + `","name":"Kagi","description":"Secrets","prefix":"KAGI","slug":"kagi","childBoards":[]}` +
		`],"pagination":{"currentPage":1,"last":true}}`
	infraJSON = `{"data":{"id":"` + idInfra + `","name":"Infra","description":"Servers","prefix":"INF","slug":"infra"}}`
)

// newRoot builds a fresh "oba kazan" tree with only this package's commands.
func newRoot() *cobra.Command {
	return clitest.AppRoot("kazan", NewCmd())
}

func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// newBoardAPI starts a mock API serving the board tree, signed in to clitest.OrgIDAcme, with no
// terminal on stdin.
func newBoardAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kazan/boards", http.StatusOK, treeJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}
