package milestone

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
	idMob    = "0a000000-0000-4000-8000-000000000002"
	idBeta   = "0f000000-0000-4000-8000-000000000001"
	idGA     = "0f000000-0000-4000-8000-000000000002"
	idBeta2  = "0f000000-0000-4000-8000-000000000003"
	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[` +
		`{"id":"` + idMob + `","name":"Mobile","prefix":"MOB","childBoards":[]}]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	milestonesPath = "/kaizen/boards/" + idOps + "/milestones"
	betaJSON       = `{"id":"` + idBeta + `","name":"Beta","status":"OPEN","targetDate":"2026-12-01","boardId":"` + idOps + `",` +
		`"boardName":"Operations","boardPrefix":"OPS","doneCount":3,"totalCount":10,"health":"ON_TRACK"}`
	gaJSON = `{"id":"` + idGA + `","name":"GA","status":"OPEN","startDate":"2026-11-25","targetDate":"2027-01-15","boardId":"` + idOps + `",` +
		`"boardName":"Operations","boardPrefix":"OPS","doneCount":0,"totalCount":4,"health":"AT_RISK"}`
	milestonesJSON = `{"data":[` + betaJSON + `,` + gaJSON + `]}`
)

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

// newAPI starts a mock API serving the board tree and the milestones reachable from OPS, signed
// in to Acme with OPS as its default board, and with stdin not a terminal.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	api.Respond("GET "+milestonesPath, http.StatusOK, milestonesJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDAcme: idOps}}); err != nil {
		t.Fatal(err)
	}
	return api
}

func milestoneRoute(method, id, suffix string) string {
	return method + " " + milestonesPath + "/" + id + suffix
}
