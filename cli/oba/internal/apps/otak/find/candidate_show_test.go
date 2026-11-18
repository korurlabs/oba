package find

import (
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestShowCandidatesColumns(t *testing.T) {
	known := NewKnownCompanies([]client.RelationListItem{{Name: "Known BV", Website: strPtr("https://known.nl")}})
	var out strings.Builder
	skipped := ShowCandidates(&out, []Candidate{
		{Name: "Acme", Website: "https://www.acme.nl/about", Summary: "Rotterdam,\n about 40 people"},
		{Name: "Other Name", Website: "http://known.nl", Summary: "ignored"},
		{Name: "Longer Name BV", Website: "", Summary: strings.Repeat("x", 90)},
	}, known)
	want := "  1. Acme            acme.nl   Rotterdam, about 40 people\n" +
		"       no contact found\n" +
		"  2. Other Name      known.nl  already in Otak as Known BV\n" +
		"  3. Longer Name BV  -         " + strings.Repeat("x", 80) + "\n" +
		"       no contact found\n"
	if out.String() != want {
		t.Errorf("out =\n%s\nwant\n%s", out.String(), want)
	}
	if !reflect.DeepEqual(skipped, map[int]bool{2: true}) {
		t.Errorf("skipped = %v", skipped)
	}
}

func TestShowCandidatesContactLines(t *testing.T) {
	role, email := "Director", "jan@acme.nl"
	var out strings.Builder
	ShowCandidates(&out, []Candidate{{
		Name: "Acme", Website: "https://acme.nl", Summary: "s",
		Contacts: []contact.FoundContact{
			{Name: "Jan", Role: &role, Email: &email, SourceURL: "https://acme.nl/team"},
			{Name: "Els", SourceURL: "https://acme.nl/contact"},
		},
	}}, NewKnownCompanies(nil))
	for _, want := range []string{
		"       contact: Jan · Director · jan@acme.nl\n       source: https://acme.nl/team\n",
		"       contact: Els\n       source: https://acme.nl/contact\n",
	} {
		if !strings.Contains(out.String(), want) {
			t.Errorf("out lacks %q:\n%s", want, out.String())
		}
	}
	if strings.Contains(out.String(), "no contact found") {
		t.Errorf("out = %s", out.String())
	}
}
