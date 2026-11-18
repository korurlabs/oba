package organization

import (
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestResolveAuthorIDsPutsMeFirst(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"id":"m1","userId":"u-a","email":"a@b.c","role":"ADMIN","status":"ACTIVE"}]}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	me := func() (string, error) { return "u-me", nil }

	ids, err := ResolveAuthorIDs(c, []string{"a@b.c", " ME ", testUserID}, me)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Join(ids, ",") != "u-me,u-a,"+testUserID {
		t.Errorf("ids = %v", ids)
	}
}

func TestResolveAuthorIDsReturnsTheMeError(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
	failed := errors.New("no user")

	_, err := ResolveAuthorIDs(c, []string{"me"}, func() (string, error) { return "", failed })
	if !errors.Is(err, failed) || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}
