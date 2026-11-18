package ticket

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestParseDestination(t *testing.T) {
	dest, err := ParseDestination(DestinationInput{Sprint: " Sprint 3 ", ToBoard: "kagi", SubTickets: "OPS-1, OPS-2"})
	if err != nil {
		t.Fatal(err)
	}
	if dest.Sprint != "Sprint 3" || dest.TargetBoard != "kagi" || dest.subTickets.mode != client.SubTicketModeSelected ||
		strings.Join(dest.subTickets.refs, ",") != "OPS-1,OPS-2" {
		t.Errorf("dest = %+v", dest)
	}
	dest, err = ParseDestination(DestinationInput{Backlog: true, SubTickets: "None"})
	if err != nil || dest.subTickets.mode != client.SubTicketModeNone {
		t.Errorf("dest %+v err %v", dest, err)
	}
}

func TestParseDestinationRefuses(t *testing.T) {
	cases := map[string]DestinationInput{
		"neither":       {SubTickets: "unfinished"},
		"both":          {Sprint: "x", Backlog: true, SubTickets: "unfinished"},
		"retired":       {Backlog: true, TargetSprintGiven: true},
		"boards differ": {Backlog: true, TargetBoard: "ops", ToBoard: "kagi", SubTickets: "unfinished"},
		"bad sub":       {Backlog: true, SubTickets: "maybe"},
		"empty sub":     {Backlog: true, SubTickets: " , "},
	}
	for name, in := range cases {
		if _, err := ParseDestination(in); err == nil {
			t.Errorf("%s: no error", name)
		}
	}
}
