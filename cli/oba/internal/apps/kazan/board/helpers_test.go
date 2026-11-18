package board

import (
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	idOps   = "0a000000-0000-4000-8000-000000000001"
	idInfra = "0a000000-0000-4000-8000-000000000002"
	idKagi  = "0a000000-0000-4000-8000-000000000003"
	idOther = "0a000000-0000-4000-8000-000000000004"

	// treeJSON is the board tree: Operations (OPS) with child Infra (INF), Kagi (KAGI), and a
	// board named "kagi2" whose prefix is "INFRA", so that "infra" matches two boards.
	treeJSON = `{"data":[` +
		`{"id":"` + idOps + `","name":"Operations","description":"Day to day work","prefix":"OPS","slug":"operations","color":"#ff0000","childBoards":[` +
		`{"id":"` + idInfra + `","name":"Infra","description":"","prefix":"INF","slug":"infra","childBoards":[]}]},` +
		`{"id":"` + idKagi + `","name":"Kagi","description":"Secrets","prefix":"KAGI","slug":"kagi","childBoards":[]},` +
		`{"id":"` + idOther + `","name":"kagi2","description":"","prefix":"INFRA","slug":"kagi2","childBoards":[]}` +
		`],"pagination":{"currentPage":1,"last":true}}`
)

// newAPI starts a mock API serving the board tree and returns it with a client in Acme.
func newAPI(t *testing.T) (*clitest.MockAPI, *client.Client) {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kazan/boards", http.StatusOK, treeJSON)
	return api, client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
}
