package sprint

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
	idS1     = "0e000000-0000-4000-8000-000000000001"
	idS2     = "0e000000-0000-4000-8000-000000000002"
	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[` +
		`{"id":"` + idMob + `","name":"Mobile","prefix":"MOB","childBoards":[]}]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	sprintsJSON = `{"data":[` +
		`{"id":"` + idS1 + `","name":"Sprint 1","status":"ACTIVE","createdAt":"2026-11-01T09:00:00Z"},` +
		`{"id":"` + idS2 + `","name":"Sprint 2","status":"PLANNED","createdAt":"2026-11-10T09:00:00Z"}]}`
	sprintsPath = "/kaizen/boards/" + idOps + "/sprints"
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

// newAPI starts a mock API serving the board tree and the sprints of OPS, signed in to Acme with
// OPS as its default board, and with stdin not a terminal.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	api.Respond("GET "+sprintsPath, http.StatusOK, sprintsJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDAcme: idOps}}); err != nil {
		t.Fatal(err)
	}
	return api
}

// captureQuery answers route with body and stores the query string of the request.
func captureQuery(api *clitest.MockAPI, route, body string, query *string) {
	api.Handle(route, func(w http.ResponseWriter, r *http.Request) {
		*query = r.URL.RawQuery
		clitest.WriteJSON(w, body)
	})
}
