package invite

import (
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/ulak/invite"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	invitationID   = "99999999-9999-4999-8999-999999999999"
	invitationPath = "/invitations/" + invitationID

	// invitationJSON expires at 22:30 UTC, which is already the next day in Istanbul.
	invitationJSON = `{"id":"` + invitationID + `","email":"ayse@example.com","role":"ADMIN","status":"PENDING",` +
		`"invitedByEmail":"a@b.c","createdAt":"2026-11-15T22:30:00Z","expiresAt":"2026-11-22T22:30:00Z"}`
	invitationsJSON = `{"data":[` + invitationJSON + `,{"id":"i2","email":"bob@example.com","role":"GUEST",` +
		`"status":"PENDING","invitedByEmail":"a@b.c","createdAt":"2026-11-14T08:00:00Z","expiresAt":"2026-11-21T08:00:00Z"}]}`
)

func TestInviteCreateSendsEmailAndRole(t *testing.T) {
	tests := []struct {
		name     string
		args     []string
		wantBody string
	}{
		{"default role", nil, `{"email":"ayse@example.com","role":"MEMBER"}`},
		{"role case ignored", []string{"--role", "admin"}, `{"email":"ayse@example.com","role":"ADMIN"}`},
		{"guest", []string{"--role", "GUEST"}, `{"email":"ayse@example.com","role":"GUEST"}`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newInviteAPI(t)
			api.Respond("POST /invitations", http.StatusCreated, `{"data":`+invitationJSON+`}`)
			out, _, err := runCLI(t, "", append([]string{"ulak", "invite", "create", " ayse@example.com "}, tt.args...)...)
			if err != nil {
				t.Fatal(err)
			}
			req := api.Request("POST /invitations")
			if req.Body != tt.wantBody {
				t.Errorf("body = %s, want %s", req.Body, tt.wantBody)
			}
			if req.Org != clitest.OrgIDAcme {
				t.Errorf("organization header = %q", req.Org)
			}
			want := "Invited ayse@example.com as ADMIN to Acme. They join when they accept in Oba; " +
				"the invitation expires 2026-11-23.\n"
			if out != want {
				t.Errorf("out = %q", out)
			}
		})
	}
}

func TestInviteCreateWithoutExpiry(t *testing.T) {
	api := newInviteAPI(t)
	api.Respond("POST /invitations", http.StatusCreated,
		`{"data":{"id":"i1","email":"ayse@example.com","role":"MEMBER","status":"PENDING","expiresAt":null}}`)
	out, _, err := runCLI(t, "", "ulak", "invite", "create", "ayse@example.com")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Invited ayse@example.com as MEMBER to Acme. They join when they accept in Oba.\n" {
		t.Errorf("out = %q", out)
	}
}

func TestInviteCreateRefusesRoleBeforeAnyCall(t *testing.T) {
	for _, given := range []string{"OWNER", "owner", "boss"} {
		t.Run(given, func(t *testing.T) {
			api := newInviteAPI(t)
			_, _, err := runCLI(t, "", "ulak", "invite", "create", "ayse@example.com", "--role", given)
			want := `unknown role "` + given + `"; allowed: ADMIN, MEMBER, GUEST`
			if err == nil || err.Error() != want {
				t.Fatalf("err = %v, want %s", err, want)
			}
			if calls := api.Calls(); len(calls) != 0 {
				t.Errorf("calls = %v", calls)
			}
		})
	}
}

func TestInviteCreateAPIErrors(t *testing.T) {
	tests := []struct {
		name, body string
		status     int
		wantErr    string
		wantExit   int
	}{
		{"pending invitation", `{"error":{"code":"INV_VAL_002","message":"A pending invitation already exists"}}`,
			http.StatusUnprocessableEntity, "INV_VAL_002: A pending invitation already exists", 1},
		{"below admin", `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`,
			http.StatusForbidden, "ORG_SEC_003: Insufficient role", 3},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newInviteAPI(t)
			api.Respond("POST /invitations", tt.status, tt.body)
			out, _, err := runCLI(t, "", "ulak", "invite", "create", "ayse@example.com")
			if err == nil || err.Error() != tt.wantErr || client.ExitCode(err) != tt.wantExit {
				t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
			}
			if out != "" {
				t.Errorf("out = %q", out)
			}
		})
	}
}

func TestInviteList(t *testing.T) {
	api := newInviteAPI(t)
	api.Respond("GET /invitations", http.StatusOK, invitationsJSON)
	out, _, err := runCLI(t, "", "ulak", "invite", "list")
	if err != nil {
		t.Fatal(err)
	}
	want := "EMAIL             ROLE   INVITED BY  EXPIRES     ID\n" +
		"ayse@example.com  ADMIN  a@b.c       2026-11-23  " + invitationID + "\n" +
		"bob@example.com   GUEST  a@b.c       2026-11-21  i2\n"
	if out != want {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
	if api.Request("GET /invitations").Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestInviteListEmpty(t *testing.T) {
	api := newInviteAPI(t)
	api.Respond("GET /invitations", http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "ulak", "invite", "list")
	if err != nil {
		t.Fatal(err)
	}
	if out != "No pending invitations.\n" {
		t.Errorf("out = %q", out)
	}
}

func TestInviteRevoke(t *testing.T) {
	question := "Revoke the invitation of ayse@example.com? (y/n) "
	tests := []struct {
		name, stdin string
		args        []string
		terminal    bool
		wantCalls   []string
		wantOut     string
		wantErr     error
	}{
		{"--yes", "", []string{"--yes"}, false, []string{"DELETE " + invitationPath},
			"Revoked invitation " + invitationID + "\n", nil},
		{"confirmed", "y\n", nil, true, []string{"GET /invitations", "DELETE " + invitationPath},
			question + "Revoked invitation " + invitationID + "\n", nil},
		{"declined", "n\n", nil, true, []string{"GET /invitations"}, question + "Cancelled.\n", nil},
		{"end of input (Ctrl-D)", "", nil, true, []string{"GET /invitations"}, question + "\nCancelled.\n", nil},
		{"no terminal", "", nil, false, nil, "", invite.ErrRevokeNeedsYes},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newInviteAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			api.Respond("GET /invitations", http.StatusOK, invitationsJSON)
			api.Respond("DELETE "+invitationPath, http.StatusOK, `{"message":"ok"}`)
			out, _, err := runCLILines(t, tt.stdin, append([]string{"ulak", "invite", "revoke", invitationID}, tt.args...)...)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if out != tt.wantOut {
				t.Errorf("out = %q", out)
			}
			if got := api.Calls(); strings.Join(got, ",") != strings.Join(tt.wantCalls, ",") {
				t.Errorf("calls = %v, want %v", got, tt.wantCalls)
			}
		})
	}
}

func TestInviteRevokeUnknownIDIsAPINotFound(t *testing.T) {
	api := newInviteAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("GET /invitations", http.StatusOK, invitationsJSON)
	api.Respond("DELETE /invitations/nope", http.StatusNotFound, `{"error":{"code":"INV_NOT_001","message":"Invitation not found"}}`)
	out, _, err := runCLI(t, "", "ulak", "invite", "revoke", "nope")
	if err == nil || err.Error() != "INV_NOT_001: Invitation not found" || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
	if out != "" {
		t.Errorf("out = %q", out)
	}
}

func TestInviteRevokeNotPending(t *testing.T) {
	api := newInviteAPI(t)
	api.Respond("DELETE "+invitationPath, http.StatusUnprocessableEntity,
		`{"error":{"code":"INV_VAL_010","message":"Invitation is not pending"}}`)
	_, _, err := runCLI(t, "", "ulak", "invite", "revoke", invitationID, "--yes")
	if err == nil || err.Error() != "INV_VAL_010: Invitation is not pending" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}
