package pr

import (
	"strings"
	"testing"
)

func TestParseTickets(t *testing.T) {
	got, err := ParseTickets("tickets", "acme-241, 0b000000-0000-4000-8000-000000000001")
	if err != nil || len(got) != 2 {
		t.Fatalf("got %v, %v", got, err)
	}
	for raw, want := range map[string]string{
		"ACME":         "--tickets: invalid ticket",
		"ACME-1,acme-1": "--tickets names acme-1 twice",
		"A-1,A-2,A-3,A-4,A-5,A-6,A-7,A-8,A-9,A-10,A-11": "at most 10 related tickets",
	} {
		if _, err := ParseTickets("tickets", raw); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%q: err = %v, want %q", raw, err, want)
		}
	}
}
