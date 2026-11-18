package member

import (
	"bytes"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	userID      = "0b000000-0000-4000-8000-000000000001"
	membersPath = "/kaizen/boards/" + idOps + "/members"
	memberJSON  = `{"data":{"id":"m1","userId":"` + userID + `","email":"ana@example.com","role":"ADMIN"}}`
	orgMembers  = `{"data":[{"id":"o1","userId":"` + userID + `","email":"Ana@Example.com","role":"MEMBER","status":"ACTIVE"}]}`
)

func TestMemberList(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+membersPath, http.StatusOK,
		`{"data":[{"userId":"u1","email":"ana@example.com","firstName":"Ana","lastName":"Silva","role":"OWNER"},`+
			`{"userId":"u2","email":"bo@example.com","firstName":"Bo","lastName":"","role":"MEMBER"}]}`)
	out, _, err := runCLI(t, "", "kazan", "member", "list")
	if err != nil {
		t.Fatal(err)
	}
	want := "NAME       EMAIL            ROLE\nAna Silva  ana@example.com  OWNER\nBo         bo@example.com   MEMBER\n"
	if out != want {
		t.Errorf("out =\n%q", out)
	}
}

// An email is looked up among the organization members, out of the --json result; the result
// is the added member.
func TestMemberAddByEmail(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK, orgMembers)
	api.Respond("POST "+membersPath, http.StatusCreated, memberJSON)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "kazan", "member", "add", "--user", "ana@example.com", "--role", "admin"); err != nil {
		t.Fatal(err)
	}
	if body := api.Request("POST " + membersPath).Body; body != `{"userId":"`+userID+`","role":"ADMIN"}` {
		t.Errorf("body = %s", body)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(buf.String(), `"id": "m1"`) {
		t.Errorf("json = %s", buf.String())
	}
}

func TestMemberAddByUserIDAlias(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+membersPath, http.StatusCreated, memberJSON)
	out, _, err := runCLI(t, "", "kazan", "member", "add", "--user-id", userID, "--role", "ADMIN")
	if err != nil || out != "Member "+userID+" added with role ADMIN.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestMemberAddRefusals(t *testing.T) {
	cases := map[string][]string{
		"--user and --role are required": {"--user", userID},
		`invalid role "boss"`:            {"--user", userID, "--role", "boss"},
	}
	for want, flags := range cases {
		api := newAPI(t)
		_, _, err := runCLI(t, "", append([]string{"kazan", "member", "add"}, flags...)...)
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%v: err = %v", flags, err)
		}
		if len(api.Calls()) != 0 {
			t.Errorf("%v: calls = %v", flags, api.Calls())
		}
	}
}

func TestMemberAddUnknownEmail(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK, orgMembers)
	_, _, err := runCLI(t, "", "kazan", "member", "add", "--user", "x@example.com", "--role", "MEMBER")
	if err == nil || !strings.Contains(err.Error(), `"x@example.com"`) {
		t.Errorf("err = %v", err)
	}
}

func TestMemberUpdate(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+membersPath+"/"+userID, http.StatusOK, memberJSON)
	out, _, err := runCLI(t, "", "kazan", "member", "update", userID, "--role", "ADMIN")
	if err != nil || out != "Member "+userID+" role updated to ADMIN.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	if body := api.Request("PUT " + membersPath + "/" + userID).Body; body != `{"role":"ADMIN"}` {
		t.Errorf("body = %s", body)
	}
}

func TestMemberUpdateNeedsRole(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "member", "update", userID)
	if err == nil || !strings.Contains(err.Error(), "--role is required") || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestMemberSpecialties(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK, orgMembers)
	route := "PUT " + membersPath + "/" + userID + "/specialties"
	api.Respond(route, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "member", "specialties", "ana@example.com", "--specialties", "developer, Architect")
	if err != nil || out != "Specialties updated for member ana@example.com.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	if body := api.Request(route).Body; body != `{"specialties":["DEVELOPER","ARCHITECT"]}` {
		t.Errorf("body = %s", body)
	}
}

func TestMemberSpecialtiesInvalid(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "member", "specialties", userID, "--specialties", "dev")
	if err == nil || !strings.Contains(err.Error(), `invalid specialty "dev"`) || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestMemberRemoveWithoutTerminal(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api.Respond("DELETE "+membersPath+"/"+userID, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "member", "remove", userID)
	if err != nil || out != "Member "+userID+" removed.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

// A cancelled remove gives null with --json, not the organization members read to resolve the
// email.
func TestMemberRemoveCancelled(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK, orgMembers)
	out, _, err := runCLI(t, "n\n", "kazan", "member", "remove", "ana@example.com")
	if err != nil || out != "Remove member ana@example.com? (y/n) Cancelled.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("removed: %v", call)
		}
	}
}

func TestMemberRemoveYesSkipsTheQuestion(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("DELETE "+membersPath+"/"+userID, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "member", "remove", userID, "--yes")
	if err != nil || out != "Member "+userID+" removed.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}
