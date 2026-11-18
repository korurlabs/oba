package client

import (
	"errors"
	"net/http"
	"testing"
)

const memberJSON = `{"id":"m1","userId":"u2","email":"b@c.d","firstName":"Bo","lastName":null,"role":"ADMIN","status":"ACTIVE"}`

func TestListMembers(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{"data":[`+memberJSON+`]}`)
	members, err := NewWithToken(srv.URL, "org-1", "tok").ListMembers()
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"GET", "/organizations/org-1/members", "org-1", ""})
	m := members[0]
	if len(members) != 1 || m.UserID != "u2" || m.Email != "b@c.d" || m.Role != "ADMIN" || m.Status != "ACTIVE" ||
		m.FirstName == nil || *m.FirstName != "Bo" || m.LastName != nil {
		t.Errorf("members = %+v", members)
	}
}

func TestChangeMemberRole(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{"data":`+memberJSON+`}`)
	member, err := NewWithToken(srv.URL, "org-1", "tok").ChangeMemberRole("u/2", "ADMIN")
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"PATCH", "/organizations/org-1/members/u%2F2/role", "org-1", `{"role":"ADMIN"}`})
	if member.Role != "ADMIN" {
		t.Errorf("member = %+v", member)
	}
}

func TestRemoveMember(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{"message":"removed"}`)
	if err := NewWithToken(srv.URL, "org-1", "tok").RemoveMember("u2"); err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"DELETE", "/organizations/org-1/members/u2", "org-1", ""})
}

func TestMemberCallsNeedOrganization(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{}`)
	c := NewWithToken(srv.URL, "", "tok")
	if _, err := c.ListMembers(); !errors.Is(err, errNoOrganization) {
		t.Errorf("ListMembers err = %v", err)
	}
	if _, err := c.ChangeMemberRole("u2", "ADMIN"); !errors.Is(err, errNoOrganization) {
		t.Errorf("ChangeMemberRole err = %v", err)
	}
	if err := c.RemoveMember("u2"); !errors.Is(err, errNoOrganization) {
		t.Errorf("RemoveMember err = %v", err)
	}
	if len(*reqs) != 0 {
		t.Errorf("requests sent: %+v", *reqs)
	}
}

func TestMemberCallForbidden(t *testing.T) {
	srv, _ := adminServer(t, http.StatusForbidden, `{"error":{"code":"ORG_SEC_006","message":"admin cannot modify owner"}}`)
	err := NewWithToken(srv.URL, "org-1", "tok").RemoveMember("u2")
	if ExitCode(err) != 3 || err.Error() != "ORG_SEC_006: admin cannot modify owner" {
		t.Errorf("err = %v, exit %d", err, ExitCode(err))
	}
}
