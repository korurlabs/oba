package attachment

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	pageID          = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
	attachmentID    = "55555555-5555-4555-8555-555555555555"
	attachmentsPath = "/storage/attachments"
)

// treeJSON is the tree of pages the caller can see, parents first, as the API lists it.
const treeJSON = `{"data":[` +
	`{"id":"r1","slug":"engineering","path":"engineering","position":0,"compliance":false,"titles":{"EN":"Engineering"},"level":"EDIT"},` +
	`{"id":"` + pageID + `","parentId":"r1","rootId":"r1","slug":"plans","path":"engineering/plans","position":0,"compliance":false,"titles":{}}` +
	`]}`

// newClient starts a mock API and returns a client of clitest.OrgIDAcme for it.
func newClient(t *testing.T) (*clitest.MockAPI, *client.Client) {
	t.Helper()
	api := clitest.NewMockAPI(t)
	return api, client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
}
