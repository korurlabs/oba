package ticket

import (
	"bytes"
	"net/http"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	idOps     = "0a000000-0000-4000-8000-000000000001"
	idKagi    = "0a000000-0000-4000-8000-000000000003"
	idT42     = "0b000000-0000-4000-8000-000000000042"
	idT43     = "0b000000-0000-4000-8000-000000000043"
	idK7      = "0b000000-0000-4000-8000-000000000007"
	idBacklog = "0c000000-0000-4000-8000-000000000001"
	idSprint3 = "0d000000-0000-4000-8000-000000000003"
	idSprintK = "0d000000-0000-4000-8000-000000000009"

	treeJSON = `{"data":[` +
		`{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]},` +
		`{"id":"` + idKagi + `","name":"Kagi","prefix":"KAGI","childBoards":[]}` +
		`],"pagination":{"currentPage":1,"last":true}}`

	sprintsJSON = `{"data":[` +
		`{"id":"` + idSprint3 + `","name":"Sprint 3","status":"ACTIVE","createdAt":"2026-11-01T00:00:00Z"}]}`
)

func ticketJSON(id, key, title, boardID, prefix string) string {
	return `{"id":"` + id + `","key":"` + key + `","title":"` + title + `","status":"TODO","priority":"HIGH",` +
		`"type":"BUG","board":{"id":"` + boardID + `","name":"Board ` + prefix + `","prefix":"` + prefix + `"},` +
		`"assignees":[{"id":"m1","userId":"u1","email":"a@b.c","firstName":"Ada","lastName":"Lovelace"}],` +
		`"sprint":{"id":"` + idSprint3 + `","name":"Sprint 3"}}`
}

// newRoot builds "oba kazan ticket" with the --board flag the kazan group carries.
func newRoot() *cobra.Command {
	root := clitest.AppRoot("kazan", NewCmd())
	for _, c := range root.Commands() {
		boardflag.Add(c)
	}
	return root
}

func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// newTicketAPI starts a mock API serving the board tree, the tickets OPS-42 and KAGI-7, the
// sprints and the backlog of both boards, signed in to clitest.OrgIDAcme with OPS as the default
// board and no terminal on stdin.
func newTicketAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kazan/boards", http.StatusOK, treeJSON)
	api.Respond("GET /kazan/tickets/by-key/OPS-42", http.StatusOK, `{"data":`+ticketJSON(idT42, "OPS-42", "Login crash", idOps, "OPS")+`}`)
	api.Respond("GET /kazan/tickets/by-key/OPS-43", http.StatusOK, `{"data":`+ticketJSON(idT43, "OPS-43", "Logout", idOps, "OPS")+`}`)
	api.Respond("GET /kazan/tickets/by-key/KAGI-7", http.StatusOK, `{"data":`+ticketJSON(idK7, "KAGI-7", "Vault", idKagi, "KAGI")+`}`)
	api.Respond("GET /kazan/boards/"+idOps+"/sprints", http.StatusOK, sprintsJSON)
	api.Respond("GET /kazan/boards/"+idKagi+"/sprints", http.StatusOK,
		`{"data":[{"id":"`+idSprintK+`","name":"Sprint 3","status":"PLANNED","createdAt":"2026-11-02T00:00:00Z"}]}`)
	api.Respond("GET /kazan/boards/"+idOps+"/backlog", http.StatusOK, `{"data":{"id":"`+idBacklog+`"}}`)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDAcme: idOps}}); err != nil {
		t.Fatal(err)
	}
	return api
}

// withJSON turns --json on for the test and returns the buffer the result is flushed to.
func withJSON(t *testing.T) *bytes.Buffer {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	return &buf
}

func flush(t *testing.T, buf *bytes.Buffer) string {
	t.Helper()
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	return buf.String()
}
