package roadmap

import (
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestSpanFromMilestones(t *testing.T) {
	start, end, ok := span(sampleRoadmap())
	if !ok {
		t.Fatal("want a window")
	}
	if got := start.Format(time.DateOnly); got != "2026-10-01" {
		t.Errorf("start = %s, want the first of the earliest month 2026-10-01", got)
	}
	if got := end.Format(time.DateOnly); got != "2026-12-31" {
		t.Errorf("end = %s, want the last of the latest month 2026-12-31", got)
	}
}

func TestSpanUsesResponseBounds(t *testing.T) {
	resp := sampleRoadmap()
	resp.From = strPtr("2026-11-01")
	resp.To = strPtr("2026-11-30")
	start, end, ok := span(resp)
	if !ok || start.Format(time.DateOnly) != "2026-11-01" || end.Format(time.DateOnly) != "2026-11-30" {
		t.Errorf("window = %v .. %v (ok %v), want the response bounds", start, end, ok)
	}

	empty := client.Roadmap{From: strPtr("2026-11-01"), To: strPtr("2026-11-30")}
	if _, _, ok := span(empty); !ok {
		t.Error("both bounds given: want a window even without milestones")
	}
	if _, _, ok := span(client.Roadmap{From: strPtr("2026-11-01")}); ok {
		t.Error("one bound and no milestones: want no window")
	}
}
