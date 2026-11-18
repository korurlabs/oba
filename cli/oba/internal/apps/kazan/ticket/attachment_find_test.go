package ticket

import (
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestFindAttachmentOfAnotherTicketIsA404(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /storage/attachments", http.StatusOK, `{"data":[{"id":"abc-DEF","fileName":"a.txt"}]}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	found, err := FindAttachment(c, "t1", "ABC-def")
	if err != nil || found.FileName != "a.txt" {
		t.Fatalf("found %+v, err %v", found, err)
	}
	_, err = FindAttachment(c, "t1", "other")
	if err == nil || client.ExitCode(err) != 2 || err.Error() != "attachment other is not attached to this ticket" {
		t.Fatalf("err = %v", err)
	}
}
