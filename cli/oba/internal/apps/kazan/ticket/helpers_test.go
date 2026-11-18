package ticket

import (
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	idOps    = "0a000000-0000-4000-8000-000000000001"
	idKagi   = "0a000000-0000-4000-8000-000000000003"
	idTicket = "0b000000-0000-4000-8000-000000000001"

	treeJSON = `{"data":[` +
		`{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]},` +
		`{"id":"` + idKagi + `","name":"Kagi","prefix":"KAGI","childBoards":[]}` +
		`],"pagination":{"currentPage":1,"last":true}}`
	ticketJSON = `{"data":{"id":"` + idTicket + `","key":"OPS-12","board":{"id":"` + idOps + `","name":"Operations","prefix":"OPS"}}}`
)

// newAPI starts a mock API serving the board tree and ticket OPS-12, and returns it with a
// client in Acme.
func newAPI(t *testing.T) (*clitest.MockAPI, *client.Client) {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	api.Respond("GET /kaizen/tickets/by-key/OPS-12", http.StatusOK, ticketJSON)
	return api, client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
}
