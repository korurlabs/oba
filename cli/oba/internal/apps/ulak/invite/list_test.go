package invite

import (
	"bytes"
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestPrintListSanitizesCells(t *testing.T) {
	expires := "2026-11-22T08:00:00Z"
	invs := []client.Invitation{
		{ID: "i1", Email: "evil@example.com\nFORGED  ROW", Role: "MEMBER", InvitedByEmail: "a@b.c\x1b[31m", ExpiresAt: &expires},
		{ID: "i2", Email: "bob@example.com", Role: "GUEST"},
	}
	var out bytes.Buffer
	if err := PrintList(&out, invs, time.UTC); err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSuffix(out.String(), "\n"), "\n")
	if len(lines) != 3 {
		t.Fatalf("lines = %q", lines)
	}
	if strings.Contains(out.String(), "\x1b") || !strings.HasPrefix(lines[1], "evil@example.com FORGED ROW ") {
		t.Errorf("out = %q", out.String())
	}
	if fields := strings.Fields(lines[2]); strings.Join(fields, " ") != "bob@example.com GUEST - - i2" {
		t.Errorf("row = %q", lines[2])
	}
}

func TestPrintListEmpty(t *testing.T) {
	var out bytes.Buffer
	if err := PrintList(&out, nil, time.UTC); err != nil {
		t.Fatal(err)
	}
	if out.String() != "No pending invitations.\n" {
		t.Errorf("out = %q", out.String())
	}
}
