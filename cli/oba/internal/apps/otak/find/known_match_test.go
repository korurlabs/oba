package find

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestKnownCompaniesMatch(t *testing.T) {
	known := NewKnownCompanies([]client.RelationListItem{
		{ID: "1", Name: "Acme  B.V.", Website: strPtr("https://www.Acme.com/about?x=1")},
		{ID: "2", Name: "Nowebsite", Website: nil},
		{ID: "3", Name: "Blank Site", Website: strPtr("  ")},
	})
	tests := []struct {
		name, candidate, website string
		want                     string
		ok                       bool
	}{
		{"name ignoring case and whitespace", "acme b.v.", "https://other.example", "Acme  B.V.", true},
		{"host ignoring scheme, www and path", "Different Name", "http://acme.com/contact#top", "Acme  B.V.", true},
		{"host bare", "Different Name", "ACME.com", "Acme  B.V.", true},
		{"name wins over host", "Nowebsite", "https://acme.com", "Nowebsite", true},
		{"relation without website matches by name", "nowebsite", "", "Nowebsite", true},
		{"unknown company", "Newco", "https://newco.io", "", false},
		{"different subdomain is a different host", "Newco", "https://shop.acme.com", "", false},
		{"empty name and website never match", "", "", "", false},
		{"blank relation website is not indexed", "Newco", " ", "", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := known.Match(tt.candidate, tt.website)
			if ok != tt.ok || got != tt.want {
				t.Errorf("match(%q, %q) = %q, %v; want %q, %v", tt.candidate, tt.website, got, ok, tt.want, tt.ok)
			}
		})
	}
}

func TestKnownCompaniesFirstRelationWins(t *testing.T) {
	known := NewKnownCompanies([]client.RelationListItem{
		{Name: "Acme", Website: strPtr("https://acme.com")},
		{Name: "ACME", Website: strPtr("https://www.acme.com")},
	})
	if got, _ := known.Match("acme", ""); got != "Acme" {
		t.Errorf("by name = %q", got)
	}
	if got, _ := known.Match("x", "acme.com"); got != "Acme" {
		t.Errorf("by host = %q", got)
	}
}
