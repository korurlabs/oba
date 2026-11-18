package contact

import (
	"strings"
	"testing"
)

func contactFor(name, email, phone, role, source string) FoundContact {
	c := FoundContact{Name: name, SourceURL: source}
	if email != "" {
		c.Email = &email
	}
	if phone != "" {
		c.Phone = &phone
	}
	if role != "" {
		c.Role = &role
	}
	return c
}

func TestClean(t *testing.T) {
	const src = "https://acme.nl/team"
	tests := []struct {
		name    string
		website string
		in      []FoundContact
		check   func(t *testing.T, got []FoundContact)
	}{
		{"empty name dropped", "https://acme.nl", []FoundContact{contactFor("  ", "", "", "", src)}, func(t *testing.T, got []FoundContact) {
			if len(got) != 0 {
				t.Errorf("got %+v", got)
			}
		}},
		{"bad source dropped", "https://acme.nl", []FoundContact{
			contactFor("A", "", "", "", "ftp://acme.nl"), contactFor("B", "", "", "", ""), contactFor("C", "", "", "", "acme.nl/team"),
		}, func(t *testing.T, got []FoundContact) {
			if len(got) != 0 {
				t.Errorf("got %+v", got)
			}
		}},
		{"private email nulled", "https://acme.nl", []FoundContact{contactFor("Jan", "jan@gmail.com", "", "", src)}, func(t *testing.T, got []FoundContact) {
			if len(got) != 1 || got[0].Email != nil {
				t.Errorf("got %+v", got)
			}
		}},
		{"subdomain email kept", "https://acme.nl", []FoundContact{contactFor("Jan", "jan@sales.acme.nl", "", "", src)}, func(t *testing.T, got []FoundContact) {
			if len(got) != 1 || got[0].Email == nil || *got[0].Email != "jan@sales.acme.nl" {
				t.Errorf("got %+v", got)
			}
		}},
		{"parent domain email kept", "https://shop.acme.nl", []FoundContact{contactFor("Jan", "jan@acme.nl", "", "", src)}, func(t *testing.T, got []FoundContact) {
			if len(got) != 1 || got[0].Email == nil {
				t.Errorf("got %+v", got)
			}
		}},
		{"email nulled without website", "", []FoundContact{contactFor("Jan", "jan@acme.nl", "", "", src)}, func(t *testing.T, got []FoundContact) {
			if len(got) != 1 || got[0].Email != nil {
				t.Errorf("got %+v", got)
			}
		}},
		{"phone with letters nulled, plain phone kept", "https://acme.nl", []FoundContact{
			contactFor("A", "", "call me", "", src), contactFor("B", "", "+31 (0)10 123-45.67", "", src),
		}, func(t *testing.T, got []FoundContact) {
			if len(got) != 2 || got[0].Phone != nil || got[1].Phone == nil {
				t.Errorf("got %+v", got)
			}
		}},
		{"long role nulled", "https://acme.nl", []FoundContact{contactFor("Jan", "", "", strings.Repeat("r", 101), src)}, func(t *testing.T, got []FoundContact) {
			if len(got) != 1 || got[0].Role != nil {
				t.Errorf("got %+v", got)
			}
		}},
		{"third and duplicate dropped", "https://acme.nl", []FoundContact{
			contactFor("Jan", "", "", "", src), contactFor(" jan ", "", "", "", src), contactFor("Els", "", "", "", src), contactFor("Kees", "", "", "", src),
		}, func(t *testing.T, got []FoundContact) {
			if len(got) != 2 || got[0].Name != "Jan" || got[1].Name != "Els" {
				t.Errorf("got %+v", got)
			}
		}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tt.check(t, Clean(tt.website, tt.in))
		})
	}
}
