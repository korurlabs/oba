package organization

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const testUserID = "0b000000-0000-4000-8000-000000000001"

func TestResolveMemberIDs(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"id":"m1","userId":"u-a","email":"A@B.c","role":"ADMIN","status":"ACTIVE"},`+
			`{"id":"m2","userId":"u-b","email":"b@b.c","role":"MEMBER","status":"ACTIVE"}]}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")

	ids, err := ResolveMemberIDs(c, []string{testUserID, "a@b.C", " ", "b@b.c"})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Join(ids, ",") != testUserID+",u-a,u-b" {
		t.Errorf("ids = %v", ids)
	}
	if len(api.Calls()) != 1 {
		t.Errorf("calls = %v", api.Calls())
	}
	_, err = ResolveMemberIDs(c, []string{"x@y.z"})
	want := `no member of the organization matches "x@y.z"; run 'oba ulak member list'`
	if err == nil || err.Error() != want || client.ExitCode(err) != 2 {
		t.Errorf("err = %v", err)
	}
}

func TestResolveMemberIDsOfIDsMakesNoCall(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	ids, err := ResolveMemberIDs(client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok"), []string{testUserID})
	if err != nil || len(ids) != 1 || len(api.Calls()) != 0 {
		t.Errorf("ids %v err %v calls %v", ids, err, api.Calls())
	}
}

func TestResolveActiveMemberIDsSkipsInactiveMembers(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"id":"m1","userId":"u-a","email":"a@b.c","role":"ADMIN","status":"ACTIVE"},`+
			`{"id":"m2","userId":"u-b","email":"b@b.c","role":"MEMBER","status":"DISABLED"}]}`)
	c := client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")

	ids, err := ResolveActiveMemberIDs(c, []string{"a@b.c"})
	if err != nil || strings.Join(ids, ",") != "u-a" {
		t.Fatalf("ids %v err %v", ids, err)
	}
	if _, err := ResolveActiveMemberIDs(c, []string{"b@b.c"}); err == nil || client.ExitCode(err) != 2 {
		t.Errorf("an inactive member resolved: err = %v", err)
	}
	if ids, err := ResolveMemberIDs(c, []string{"b@b.c"}); err != nil || strings.Join(ids, ",") != "u-b" {
		t.Errorf("ResolveMemberIDs ids %v err %v", ids, err)
	}
}
