package icp

import (
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// The values are shown sanitized and aligned, an ICP on one line with its columns padded.
func TestPrintBrainstormContext(t *testing.T) {
	industry := "SOFTWARE\x1b]0;pwned\x07"
	country := "NL"
	minSize, maxSize := "SIZE_11_50", "SIZE_51_200"
	role := "Chief\nFinance Officer"
	x := BrainstormContext{
		Profile: client.OrganizationProfile{Name: " Acme\u202e ", Industry: &industry, Country: &country},
		Icps: []client.Icp{
			{Name: "Benelux banks", Industry: "FINANCE", Country: &country, CompanySizeMin: &minSize, CompanySizeMax: &maxSize, TargetPersonRole: &role},
			{Name: "Insurers", Industry: "INSURANCE"},
		},
	}
	var out strings.Builder
	PrintBrainstormContext(&out, x)
	want := "Step 1 of 3 · What Oba knows about Acme\n" +
		"  description:   -   (empty)\n" +
		"  industry:      SOFTWARE\n" +
		"  company size:  -   (empty)\n" +
		"  country:       NL\n" +
		"  website:       -   (empty)\n" +
		"  ICPs (2):\n" +
		"    1. Benelux banks  FINANCE    NL   SIZE_11_50 to SIZE_51_200  Chief Finance Officer\n" +
		"    2. Insurers       INSURANCE  any  any                        any\n" +
		"\n" +
		"  ! No description yet. You can add one in the next step.\n" +
		"    It is used for this brainstorm only; to keep it, add it to the Acme profile in Oba.\n"
	if out.String() != want {
		t.Errorf("got:\n%s\nwant:\n%s", out.String(), want)
	}
}

// A website is marked as read by Claude, no ICPs read "none yet", and a description drops the note.
func TestPrintBrainstormContextWithoutIcps(t *testing.T) {
	description := "Audits."
	website := "https://acme.example"
	var out strings.Builder
	PrintBrainstormContext(&out, BrainstormContext{Profile: client.OrganizationProfile{Name: "Acme", Description: &description, Website: &website}})
	if got := out.String(); !strings.HasSuffix(got, "  website:       https://acme.example   (Claude will read this site)\n  ICPs:          none yet\n") {
		t.Errorf("got:\n%s", got)
	}
}

func TestPrintBrainstormContextWithoutContext(t *testing.T) {
	var out strings.Builder
	PrintBrainstormContext(&out, BrainstormContext{Profile: client.OrganizationProfile{Name: "Acme"}})
	if got := out.String(); got != "Step 1 of 3 · Oba has no profile for Acme yet.\n" {
		t.Errorf("got:\n%s", got)
	}
}

// IcpRows gives the padded columns without number or indent, an open field as "any", and one
// line per value, which are the rows the brainstorm context prints after its own numbering.
func TestIcpRows(t *testing.T) {
	industry := "FINANCE"
	country := "NL"
	minSize, maxSize := "SIZE_11_50", "SIZE_51_200"
	role := "Chief\nFinance Officer"
	rows := IcpRows([]client.Icp{
		{Name: "Benelux banks", Industry: industry, Country: &country, CompanySizeMin: &minSize, CompanySizeMax: &maxSize, TargetPersonRole: &role},
		{Name: "Insurers", Industry: "INSURANCE"},
	})
	want := []string{
		"Benelux banks  FINANCE    NL   SIZE_11_50 to SIZE_51_200  Chief Finance Officer",
		"Insurers       INSURANCE  any  any                        any",
	}
	if !reflect.DeepEqual(rows, want) {
		t.Errorf("got %q, want %q", rows, want)
	}
	if got := IcpRows(nil); len(got) != 0 {
		t.Errorf("rows of no ICPs = %q", got)
	}
}
