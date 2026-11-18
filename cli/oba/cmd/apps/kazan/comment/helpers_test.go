package comment

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
	idOps     = "0a000000-0000-4000-8000-000000000001"
	idTicket  = "0b000000-0000-4000-8000-000000000001"
	idComment = "0c000000-0000-4000-8000-000000000001"

	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	ticketJSON   = `{"data":{"id":"` + idTicket + `","key":"OPS-12","board":{"id":"` + idOps + `","name":"Operations","prefix":"OPS"}}}`
	commentsPath = "/kaizen/boards/" + idOps + "/tickets/" + idTicket + "/comments"
	commentsJSON = `{"data":[` +
		`{"id":"` + idComment + `","content":"First\nline","authorFirstName":"Ada","authorLastName":"Lovelace","createdAt":"2026-11-18T09:00:00Z"},` +
		`{"id":"c2","content":"Second","authorEmail":"b@x.io","createdAt":"2026-11-18T10:00:00Z"}]}`
)

// newRoot builds "oba kazan comment" with the persistent --board of the kazan group.
func newRoot() *cobra.Command {
	group := &cobra.Command{Use: "kazan"}
	boardflag.Add(group)
	group.AddCommand(NewCmd())
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	return root
}

// newAPI starts a mock API with the board tree, ticket OPS-12 and its comments, signed in to
// clitest.OrgIDAcme, with no terminal on stdin.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	api.Respond("GET /kaizen/tickets/by-key/OPS-12", http.StatusOK, ticketJSON)
	api.Respond("GET "+commentsPath, http.StatusOK, commentsJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

// captureJSON turns on --json output for the test and returns a function that flushes it.
func captureJSON(t *testing.T) func() string {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	return func() string {
		if err := jsonout.Flush(); err != nil {
			t.Fatal(err)
		}
		return buf.String()
	}
}
