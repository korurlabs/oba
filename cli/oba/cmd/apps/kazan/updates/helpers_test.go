package updates

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
	idKagi    = "0a000000-0000-4000-8000-000000000002"
	idTicket1 = "0b000000-0000-4000-8000-000000000001"
	idTicket2 = "0b000000-0000-4000-8000-000000000002"
	idUpdate  = "99999999-9999-4999-8999-999999999999"

	treeJSON = `{"data":[` +
		`{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]},` +
		`{"id":"` + idKagi + `","name":"Kagi","prefix":"KAGI","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	ticket1ByKeyJSON = `{"data":{"id":"` + idTicket1 + `","key":"OPS-7","title":"Menu","status":"IN_PROGRESS","percentage":30,"board":{"id":"` + idOps + `","name":"Operations","prefix":"OPS"}}}`
	ticket2ByKeyJSON = `{"data":{"id":"` + idTicket2 + `","key":"KAGI-2","title":"Vault","status":"TODO","percentage":55,"board":{"id":"` + idKagi + `","name":"Kagi","prefix":"KAGI"}}}`
	ticket1JSON      = `{"data":{"id":"` + idTicket1 + `","key":"OPS-7","title":"Menu","status":"IN_PROGRESS","percentage":30}}`
	ticket2JSON      = `{"data":{"id":"` + idTicket2 + `","key":"KAGI-2","title":"Vault","status":"TODO","percentage":55}}`

	updatesPath = "/kaizen/updates"
	updatePath  = updatesPath + "/" + idUpdate
)

// newRoot builds "oba kazan updates" with the persistent --board of the kazan group.
func newRoot() *cobra.Command {
	group := &cobra.Command{Use: "kazan"}
	boardflag.Add(group)
	group.AddCommand(NewCmd())
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	return root
}

// newAPI starts a mock API with two boards and tickets OPS-7 and KAGI-2, signed in to
// clitest.OrgIDAcme, with no terminal on stdin.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	api.Respond("GET /kaizen/tickets/by-key/OPS-7", http.StatusOK, ticket1ByKeyJSON)
	api.Respond("GET /kaizen/tickets/by-key/KAGI-2", http.StatusOK, ticket2ByKeyJSON)
	api.Respond("GET /kaizen/boards/"+idOps+"/tickets/"+idTicket1, http.StatusOK, ticket1JSON)
	api.Respond("GET /kaizen/boards/"+idKagi+"/tickets/"+idTicket2, http.StatusOK, ticket2JSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

func run(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, append([]string{"kazan", "updates"}, args...)...)
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

// writes returns the recorded requests that are not reads.
func writes(api *clitest.MockAPI) []clitest.RecordedRequest {
	var out []clitest.RecordedRequest
	for _, r := range api.Requests() {
		if r.Method != http.MethodGet {
			out = append(out, r)
		}
	}
	return out
}

// queryOf serves body on route and records the query string of each request into got.
func queryOf(api *clitest.MockAPI, route, body string, got *string) {
	api.Handle(route, func(w http.ResponseWriter, r *http.Request) {
		*got = r.URL.RawQuery
		clitest.WriteJSON(w, body)
	})
}
