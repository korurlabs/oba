package updates

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestReportZoneLoadsEuropeIstanbul(t *testing.T) {
	zone, err := reportZone()
	if err != nil {
		t.Fatal(err)
	}
	if got := reportClock("2026-10-16T07:30:00Z", zone); got != "10:30" {
		t.Errorf("deadline = %s, want 10:30", got)
	}
}

func TestMissingStatus(t *testing.T) {
	zone, err := reportZone()
	if err != nil {
		t.Fatal(err)
	}
	posted := "2026-10-16T08:05:00Z"
	cases := []struct {
		member client.DailyUpdateMissingMember
		passed bool
		want   string
	}{
		{client.DailyUpdateMissingMember{PostedAt: &posted}, true, "late 11:05"},
		{client.DailyUpdateMissingMember{}, true, "missing"},
		{client.DailyUpdateMissingMember{}, false, "pending"},
	}
	for _, tc := range cases {
		if got := missingStatus(tc.member, tc.passed, zone); got != tc.want {
			t.Errorf("status = %q, want %q", got, tc.want)
		}
	}
}

func TestReportClockKeepsAnUnreadableInstant(t *testing.T) {
	zone, err := reportZone()
	if err != nil {
		t.Fatal(err)
	}
	if got := reportClock("soon", zone); got != "soon" {
		t.Errorf("got %q", got)
	}
}
