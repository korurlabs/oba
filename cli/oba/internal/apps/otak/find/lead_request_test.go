package find

import (
	"strings"
	"testing"
)

func TestLeadRequestDropsInvalidWebsiteAndCountry(t *testing.T) {
	allowed := newAllowedValues(LeadEnums{Countries: []string{"NL"}})
	tests := []struct {
		name, website, country, wantWebsite, wantCountry string
	}{
		{"valid", " https://acme.nl/about ", " NL ", "https://acme.nl/about", "NL"},
		{"no scheme", "acme.nl", "NL", "", "NL"},
		{"no dot in host", "https://localhost", "NL", "", "NL"},
		{"space inside", "https://acme .nl", "NL", "", "NL"},
		{"too long", "https://acme.nl/" + strings.Repeat("a", 500), "NL", "", "NL"},
		{"unknown country", "https://acme.nl", "XX", "https://acme.nl", ""},
		{"lower case country", "https://acme.nl", "nl", "https://acme.nl", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := leadRequest(candidate("  Acme ", tt.website, tt.country, " fits "), "icp-1", allowed)
			if got.Website != tt.wantWebsite || got.Country != tt.wantCountry {
				t.Errorf("website %q country %q; want %q %q", got.Website, got.Country, tt.wantWebsite, tt.wantCountry)
			}
			if got.Name != "Acme" || got.Type != "LEAD" || got.Stage != "NEW" || got.Source != "RESEARCH" || got.IcpID != "icp-1" {
				t.Errorf("request = %+v", got)
			}
			if got.Notes != "Found by oba otak find: fits" {
				t.Errorf("notes = %q", got.Notes)
			}
		})
	}
}

func TestLeadRequestCutsNotesToLimit(t *testing.T) {
	got := leadRequest(candidate("Acme", "", "", strings.Repeat("é", 11000)), "icp-1", allowedValues{})
	if n := len([]rune(got.Notes)); n != 10000 {
		t.Errorf("notes runes = %d", n)
	}
}

func TestLeadRequestCityAndSize(t *testing.T) {
	allowed := newAllowedValues(LeadEnums{Countries: []string{"NL"}, Sizes: []string{"SIZE_11_50"}})
	tests := []struct {
		name               string
		city, size         *string
		wantCity, wantSize string
	}{
		{"kept and trimmed", strPtr(" Rotterdam "), strPtr("SIZE_11_50"), "Rotterdam", "SIZE_11_50"},
		{"null", nil, nil, "", ""},
		{"city at limit", strPtr(strings.Repeat("é", 100)), nil, strings.Repeat("é", 100), ""},
		{"city over limit dropped", strPtr(strings.Repeat("é", 101)), nil, "", ""},
		{"unknown size dropped", nil, strPtr("HUGE"), "", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := candidate("Acme", "https://acme.nl", "NL", "fits")
			c.City, c.Size = tt.city, tt.size
			got := leadRequest(c, "icp-1", allowed)
			if got.City != tt.wantCity || got.Size != tt.wantSize {
				t.Errorf("city %q size %q; want %q %q", got.City, got.Size, tt.wantCity, tt.wantSize)
			}
		})
	}
}
