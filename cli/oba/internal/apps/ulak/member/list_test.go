package member

import (
	"bytes"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func ptr(s string) *string { return &s }

func TestPrintList(t *testing.T) {
	members := []client.Member{
		{UserID: "u-1", Email: "ana@example.com", FirstName: ptr("Ana"), LastName: ptr("Silva"), Role: "OWNER", Status: "ACTIVE"},
		{UserID: "u-2", Email: "bo@example.com", FirstName: ptr("Bo"), Role: "ADMIN", Status: "ACTIVE"},
		{UserID: "u-3", Email: "cy@example.com", LastName: ptr(" "), Role: "GUEST", Status: "SUSPENDED"},
	}
	var out bytes.Buffer
	if err := PrintList(&out, members); err != nil {
		t.Fatal(err)
	}
	want := "NAME       EMAIL            ROLE   STATUS     USER ID\n" +
		"Ana Silva  ana@example.com  OWNER  ACTIVE     u-1\n" +
		"Bo         bo@example.com   ADMIN  ACTIVE     u-2\n" +
		"-          cy@example.com   GUEST  SUSPENDED  u-3\n"
	if out.String() != want {
		t.Errorf("out =\n%s\nwant\n%s", out.String(), want)
	}
}

func TestPrintListEmpty(t *testing.T) {
	var out bytes.Buffer
	if err := PrintList(&out, nil); err != nil {
		t.Fatal(err)
	}
	if out.String() != "No members.\n" {
		t.Errorf("out = %q", out.String())
	}
}

func TestPrintListSanitizesCells(t *testing.T) {
	members := []client.Member{{
		UserID: "u-1", Email: "a@b.c\nForged  row", FirstName: ptr("An\x1b[31ma"), LastName: ptr("S\tilva\r\a"),
		Role: "MEMBER", Status: "ACTIVE",
	}}
	var out bytes.Buffer
	if err := PrintList(&out, members); err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSuffix(out.String(), "\n"), "\n")
	if len(lines) != 2 {
		t.Fatalf("lines = %q", lines)
	}
	if strings.ContainsAny(out.String(), "\x1b\r\a") || !strings.HasPrefix(lines[1], "Ana S ilva  a@b.c Forged row  MEMBER") {
		t.Errorf("out = %q", out.String())
	}
}

func TestRank(t *testing.T) {
	ordered := []string{"OWNER", "ADMIN", "MEMBER", "GUEST", "SOMETHING"}
	for i := 1; i < len(ordered); i++ {
		if rank(ordered[i-1]) >= rank(ordered[i]) {
			t.Errorf("%s does not sort before %s", ordered[i-1], ordered[i])
		}
	}
}
