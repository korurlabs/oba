package project

import (
	"net/http"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	idOps    = "0a000000-0000-4000-8000-000000000001"
	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
)

// newRoot builds a fresh "oba kazan" tree with the --board flag and only this package's commands.
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

// newAPI starts a mock API serving the board tree, signed in to clitest.OrgIDAcme, with OPS as the
// default board of Acme.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDAcme: idOps}}); err != nil {
		t.Fatal(err)
	}
	return api
}
