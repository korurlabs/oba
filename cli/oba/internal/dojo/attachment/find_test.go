package attachment

import (
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestFindMatchesTheIDCaseInsensitively(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /storage/attachments", http.StatusOK, `{"data":[{"id":"abc-DEF","fileName":"a.txt"}]}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	found, ok, err := Find(c, "TICKET", "t1", "ABC-def")
	if err != nil || !ok || found.FileName != "a.txt" {
		t.Fatalf("found %+v %v, err %v", found, ok, err)
	}
	if _, ok, err := Find(c, "TICKET", "t1", "other"); err != nil || ok {
		t.Fatalf("ok %v, err %v", ok, err)
	}
}
