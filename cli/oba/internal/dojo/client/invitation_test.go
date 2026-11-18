package client

import (
	"net/http"
	"testing"
)

const invitationJSON = `{"id":"i1","organizationId":"org-1","organizationName":"Acme","email":"new@c.d","role":"MEMBER",` +
	`"status":"PENDING","invitedByEmail":"a@b.c","acceptedAt":null,"expiresAt":"2026-11-22T10:00:00Z","declinedAt":null,` +
	`"createdAt":"2026-11-15T10:00:00Z"}`

func TestCreateInvitation(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusCreated, `{"data":`+invitationJSON+`}`)
	inv, err := NewWithToken(srv.URL, "org-1", "tok").CreateInvitation("new@c.d", "MEMBER")
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"POST", "/invitations", "org-1", `{"email":"new@c.d","role":"MEMBER"}`})
	if inv.ID != "i1" || inv.Email != "new@c.d" || inv.Role != "MEMBER" || inv.Status != "PENDING" ||
		inv.InvitedByEmail != "a@b.c" || inv.CreatedAt != "2026-11-15T10:00:00Z" ||
		inv.ExpiresAt == nil || *inv.ExpiresAt != "2026-11-22T10:00:00Z" {
		t.Errorf("invitation = %+v", inv)
	}
}

func TestCreateInvitationRefused(t *testing.T) {
	srv, _ := adminServer(t, http.StatusUnprocessableEntity, `{"error":{"code":"INV_VAL_002","message":"pending invitation exists"}}`)
	_, err := NewWithToken(srv.URL, "org-1", "tok").CreateInvitation("new@c.d", "MEMBER")
	if ExitCode(err) != 1 || err.Error() != "INV_VAL_002: pending invitation exists" {
		t.Errorf("err = %v, exit %d", err, ExitCode(err))
	}
}

func TestListInvitations(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{"data":[`+invitationJSON+`]}`)
	invs, err := NewWithToken(srv.URL, "org-1", "tok").ListInvitations()
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"GET", "/invitations", "org-1", ""})
	if len(invs) != 1 || invs[0].ID != "i1" {
		t.Errorf("invitations = %+v", invs)
	}
}

func TestRevokeInvitation(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{"message":"revoked"}`)
	if err := NewWithToken(srv.URL, "org-1", "tok").RevokeInvitation("i 1"); err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"DELETE", "/invitations/i%201", "org-1", ""})
}

func TestRevokeInvitationNotFound(t *testing.T) {
	srv, _ := adminServer(t, http.StatusNotFound, `{"error":{"code":"INV_NOT_001","message":"invitation not found"}}`)
	err := NewWithToken(srv.URL, "org-1", "tok").RevokeInvitation("i1")
	if ExitCode(err) != 2 {
		t.Errorf("err = %v, exit %d", err, ExitCode(err))
	}
}
