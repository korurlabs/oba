package page

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// treeJSON is a tree of two roots, parents first, as the API lists it.
const treeJSON = `{"data":[` +
	`{"id":"r1","slug":"engineering","path":"engineering","position":1,"compliance":false,"titles":{"EN":"Engineering"},"level":"EDIT"},` +
	`{"id":"r2","slug":"isms","path":"isms","position":0,"compliance":true,"titles":{"TR":"BGYS"},"level":"VIEW"},` +
	`{"id":"p2","parentId":"r1","rootId":"r1","slug":"runbooks","path":"engineering/runbooks","position":0,"compliance":false,"titles":{}},` +
	`{"id":"p1","parentId":"r1","rootId":"r1","slug":"plans","path":"engineering/plans","position":0,"compliance":false,"titles":{"EN":"Plans","TR":"Planlar"}},` +
	`{"id":"p3","parentId":"p1","rootId":"r1","slug":"q4","path":"engineering/plans/q4","position":0,"compliance":true,"titles":{"TR":"Ç4"}},` +
	`{"id":"p4","rootId":"r1","slug":"engineering-x","path":"engineering-x","position":2,"compliance":false,"titles":{}}` +
	`]}`

// newClient starts a mock API and returns a client of clitest.OrgIDAcme for it.
func newClient(t *testing.T) (*clitest.MockAPI, *client.Client) {
	t.Helper()
	api := clitest.NewMockAPI(t)
	return api, client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
}
