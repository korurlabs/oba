package organization

import (
	"bytes"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestPrintList(t *testing.T) {
	orgs := []client.Organization{
		{ID: "o1", Name: "Acme", Role: "OWNER", Status: "ACTIVE"},
		{ID: "o2", Name: "Korur B.V.", Role: "MEMBER", Status: "ACTIVE"},
	}
	var out bytes.Buffer
	if err := PrintList(&out, orgs, "o2"); err != nil {
		t.Fatal(err)
	}
	want := "   NAME        ROLE    STATUS  ID\n" +
		"   Acme        OWNER   ACTIVE  o1\n" +
		"*  Korur B.V.  MEMBER  ACTIVE  o2\n"
	if out.String() != want {
		t.Errorf("got\n%q\nwant\n%q", out.String(), want)
	}
}

func TestPrintListEmpty(t *testing.T) {
	var out bytes.Buffer
	if err := PrintList(&out, nil, "o1"); err != nil {
		t.Fatal(err)
	}
	if out.String() != "You belong to no organization.\n" {
		t.Errorf("got %q", out.String())
	}
}

func TestPrintListSanitizesCells(t *testing.T) {
	orgs := []client.Organization{
		{ID: "o1", Name: "Evil\n*  Fake  OWNER  ACTIVE  o9", Role: "MEMBER", Status: "ACTIVE"},
		{ID: "o2", Name: "Red\u001b[31m\tCorp\r\x07", Role: "ADMIN", Status: "ACTIVE"},
	}
	var out bytes.Buffer
	if err := PrintList(&out, orgs, ""); err != nil {
		t.Fatal(err)
	}
	want := "  NAME                         ROLE    STATUS  ID\n" +
		"  Evil * Fake OWNER ACTIVE o9  MEMBER  ACTIVE  o1\n" +
		"  Red Corp                     ADMIN   ACTIVE  o2\n"
	if out.String() != want {
		t.Errorf("got\n%q\nwant\n%q", out.String(), want)
	}
}
