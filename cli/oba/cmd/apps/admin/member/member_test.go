package member

import (
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/admin/member"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestMemberList(t *testing.T) {
	api := newMemberAPI(t)
	out, _, err := runCLI(t, "", "admin", "member", "list")
	if err != nil {
		t.Fatal(err)
	}
	want := "NAME        EMAIL              ROLE    STATUS  USER ID\n" +
		"Olga Owner  owner@example.com  OWNER   ACTIVE  u-1\n" +
		"Ana         ana@example.com    MEMBER  ACTIVE  " + anaID + "\n" +
		"-           Bob@example.com    MEMBER  ACTIVE  u-3\n" +
		"Zed Guest   zed@example.com    GUEST   ACTIVE  u-4\n"
	if out != want {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
	if got := api.Request("GET " + membersPath).Org; got != clitest.OrgIDAcme {
		t.Errorf("organization header = %q", got)
	}
}

func TestMemberListEmpty(t *testing.T) {
	api := newMemberAPI(t)
	api.Respond("GET "+membersPath, http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "admin", "member", "list")
	if err != nil {
		t.Fatal(err)
	}
	if out != "No members.\n" {
		t.Errorf("out = %q", out)
	}
}

func TestMemberListSanitizesCells(t *testing.T) {
	api := newMemberAPI(t)
	api.Respond("GET "+membersPath, http.StatusOK, `{"data":[`+
		`{"id":"m1","userId":"u-1","email":"a@b.c\nOlga  x@y.z  OWNER","firstName":"\u001b[2JAna","role":"MEMBER","status":"ACTIVE"}]}`)
	out, _, err := runCLI(t, "", "admin", "member", "list")
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSuffix(out, "\n"), "\n")
	if len(lines) != 2 || strings.Contains(out, "\x1b") || !strings.HasPrefix(lines[1], "Ana   a@b.c Olga x@y.z OWNER  MEMBER") {
		t.Errorf("out = %q", out)
	}
}

func TestMemberListNoOrganization(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	clitest.LoginTo(t, api.URL(), "")
	_, _, err := runCLI(t, "", "admin", "member", "list")
	if err == nil || !strings.Contains(err.Error(), "No organization selected") {
		t.Fatalf("err = %v", err)
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestMemberRole(t *testing.T) {
	tests := []struct {
		name, value, role string
	}{
		{"by user id", anaID, "ADMIN"},
		{"by e-mail in another case", "ANA@Example.com", "guest"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newMemberAPI(t)
			api.Respond("PATCH "+anaPath+"/role", http.StatusOK,
				`{"data":{"id":"m2","userId":"`+anaID+`","email":"ana@example.com","role":"`+strings.ToUpper(tt.role)+`","status":"ACTIVE"}}`)
			out, _, err := runCLI(t, "", "admin", "member", "role", tt.value, tt.role)
			if err != nil {
				t.Fatal(err)
			}
			if want := "ana@example.com is now " + strings.ToUpper(tt.role) + " in Acme\n"; out != want {
				t.Errorf("out = %q, want %q", out, want)
			}
			req := api.Request("PATCH " + anaPath + "/role")
			if req.Body != `{"role":"`+strings.ToUpper(tt.role)+`"}` || req.Org != clitest.OrgIDAcme {
				t.Errorf("body = %s, org = %q", req.Body, req.Org)
			}
		})
	}
}

func TestMemberRoleRefusedBeforeAnyCall(t *testing.T) {
	for _, given := range []string{"OWNER", "owner", "boss"} {
		t.Run(given, func(t *testing.T) {
			api := newMemberAPI(t)
			_, _, err := runCLI(t, "", "admin", "member", "role", anaID, given)
			want := `unknown role "` + given + `"; allowed: ADMIN, MEMBER, GUEST`
			if err == nil || err.Error() != want {
				t.Fatalf("err = %v", err)
			}
			if calls := api.Calls(); len(calls) != 0 {
				t.Errorf("calls = %v", calls)
			}
		})
	}
}

func TestMemberNoMatch(t *testing.T) {
	tests := []struct {
		name string
		args []string
	}{
		{"role", []string{"role", "nobody@example.com", "ADMIN"}},
		{"remove", []string{"remove", "nobody@example.com", "--yes"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newMemberAPI(t)
			_, _, err := runCLI(t, "", append([]string{"admin", "member"}, tt.args...)...)
			want := `no member of Acme matches "nobody@example.com"; run 'oba admin member list'`
			if err == nil || err.Error() != want || client.ExitCode(err) != 2 {
				t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
			}
			for _, call := range api.Calls() {
				if !strings.HasPrefix(call, "GET ") {
					t.Errorf("write sent: %v", api.Calls())
				}
			}
		})
	}
}

func TestMemberRoleForbidden(t *testing.T) {
	tests := []struct{ code, message string }{
		{"ORG_SEC_004", "You cannot change your own role"},
		{"ORG_SEC_006", "Admins cannot modify owners"},
		{"ORG_SEC_003", "Insufficient role"},
	}
	for _, tt := range tests {
		t.Run(tt.code, func(t *testing.T) {
			api := newMemberAPI(t)
			api.Respond("PATCH "+anaPath+"/role", http.StatusForbidden,
				`{"error":{"code":"`+tt.code+`","message":"`+tt.message+`"}}`)
			_, _, err := runCLI(t, "", "admin", "member", "role", anaID, "ADMIN")
			if err == nil || err.Error() != tt.code+": "+tt.message || client.ExitCode(err) != 3 {
				t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
			}
		})
	}
}

func TestMemberRemove(t *testing.T) {
	const question = "Remove ana@example.com from Acme? (y/n) "
	const removed = "Removed ana@example.com from Acme\n"
	tests := []struct {
		name, stdin string
		args        []string
		terminal    bool
		wantRemove  bool
		wantOut     string
		wantErr     error
	}{
		{"--yes", "", []string{"--yes"}, false, true, removed, nil},
		{"-y", "", []string{"-y"}, false, true, removed, nil},
		{"confirmed", "y\n", nil, true, true, question + removed, nil},
		{"declined", "n\n", nil, true, false, question + "Cancelled.\n", nil},
		{"Ctrl-D", "", nil, true, false, question + "\nCancelled.\n", nil},
		{"no terminal", "", nil, false, false, "", member.ErrRemoveNeedsYes},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newMemberAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			api.Respond("DELETE "+anaPath, http.StatusOK, `{"message":"ok"}`)
			out, _, err := runCLILines(t, tt.stdin, append([]string{"admin", "member", "remove", "ana@example.com"}, tt.args...)...)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if out != tt.wantOut {
				t.Errorf("out = %q, want %q", out, tt.wantOut)
			}
			removedCall := false
			for _, call := range api.Calls() {
				if call == "DELETE "+anaPath {
					removedCall = true
				}
			}
			if removedCall != tt.wantRemove {
				t.Errorf("calls = %v", api.Calls())
			}
			if tt.wantErr != nil && len(api.Calls()) != 0 {
				t.Errorf("calls before refusal = %v", api.Calls())
			}
		})
	}
}

func TestMemberRemoveByUserID(t *testing.T) {
	api := newMemberAPI(t)
	api.Respond("DELETE "+anaPath, http.StatusOK, `{"message":"ok"}`)
	out, _, err := runCLI(t, "", "admin", "member", "remove", anaID, "--yes")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Removed ana@example.com from Acme\n" || api.Request("DELETE "+anaPath).Org != clitest.OrgIDAcme {
		t.Errorf("out = %q, calls = %v", out, api.Calls())
	}
}

func TestMemberRemoveForbidden(t *testing.T) {
	api := newMemberAPI(t)
	api.Respond("DELETE "+anaPath, http.StatusForbidden, `{"error":{"code":"ORG_SEC_006","message":"Admins cannot modify owners"}}`)
	_, _, err := runCLI(t, "", "admin", "member", "remove", anaID, "--yes")
	if err == nil || err.Error() != "ORG_SEC_006: Admins cannot modify owners" || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}
