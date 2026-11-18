package find

import (
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
)

func TestCountContacts(t *testing.T) {
	cands := []Candidate{
		{Contacts: []contact.FoundContact{{Name: "a"}, {Name: "b"}}}, {Contacts: []contact.FoundContact{{Name: "c"}}}, {},
	}
	if got := CountContacts(cands, []int{1, 3}); got != 2 {
		t.Errorf("got %d", got)
	}
	if got := CountContacts(cands, []int{1, 2, 3}); got != 3 {
		t.Errorf("got %d", got)
	}
}

func TestParseChoice(t *testing.T) {
	skipped := map[int]bool{2: true}
	tests := []struct {
		name, in string
		want     Choice
		errPart  string
	}{
		{"list", "1,3", Choice{Picks: []int{1, 3}}, ""},
		{"range", "1-1", Choice{Picks: []int{1}}, ""},
		{"all", "all", Choice{Picks: []int{1, 3}}, ""},
		{"all upper", "ALL", Choice{Picks: []int{1, 3}}, ""},
		{"refine", "r", Choice{Refine: true}, ""},
		{"quit upper", " Q ", Choice{Quit: true}, ""},
		{"empty", "", Choice{}, "pick numbers, r or q"},
		{"zero", "0", Choice{}, "out of range"},
		{"above count", "4", Choice{}, "out of range"},
		{"reversed", "3-1", Choice{}, "out of range"},
		{"not a number", "x", Choice{}, "not a number"},
		{"skipped", "2", Choice{}, "2 is already in Otak"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseChoice(tt.in, 3, skipped)
			if tt.errPart != "" {
				if err == nil || !strings.Contains(err.Error(), tt.errPart) {
					t.Fatalf("err = %v, want containing %q", err, tt.errPart)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("got %+v, want %+v", got, tt.want)
			}
		})
	}
	if _, err := ParseChoice("all", 2, map[int]bool{1: true, 2: true}); err == nil {
		t.Error("all with every candidate skipped accepted")
	}
}
