package roadmap

import (
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

func strPtr(s string) *string { return &s }

func mustDate(t *testing.T, s string) time.Time {
	t.Helper()
	d, err := dates.ParseDate(s)
	if err != nil {
		t.Fatalf("ParseDate(%q): %v", s, err)
	}
	return d
}

// sampleRoadmap has one bar (start to target) and one diamond (target only),
// which spans the window 2026-10-01 .. 2026-12-31 (91 days).
func sampleRoadmap() client.Roadmap {
	return client.Roadmap{
		Boards: []client.RoadmapBoard{
			{
				ID:     "b1",
				Name:   "Kagi",
				Prefix: "KAGI",
				Milestones: []client.Milestone{
					{ID: "m1", Name: "Beta", StartDate: strPtr("2026-10-10"), TargetDate: "2026-11-20", DoneCount: 3, TotalCount: 10, Health: "ON_TRACK"},
					{ID: "m2", Name: "GA", TargetDate: "2026-12-05", DoneCount: 0, TotalCount: 4, Health: "AT_RISK"},
				},
			},
			{ID: "b2", Name: "Kagi Mobile", Prefix: "KAGIM"},
		},
	}
}

func TestDateColumn(t *testing.T) {
	start := mustDate(t, "2026-10-01")
	end := mustDate(t, "2026-12-31")
	cases := []struct {
		date string
		want int
	}{
		{"2026-09-01", 0},  // before the window: clamped
		{"2026-10-01", 0},  // first day
		{"2026-10-10", 3},  // 9 * 39 / 91
		{"2026-11-20", 21}, // 50 * 39 / 91
		{"2026-12-31", 39}, // last day
		{"2027-02-01", 39}, // after the window: clamped
	}
	for _, tc := range cases {
		if got := dateColumn(mustDate(t, tc.date), start, end, 40); got != tc.want {
			t.Errorf("dateColumn(%s) = %d, want %d", tc.date, got, tc.want)
		}
	}
	if got := dateColumn(start, start, start, 40); got != 0 {
		t.Errorf("one-day window: dateColumn = %d, want 0", got)
	}
}

func TestChartColumns(t *testing.T) {
	cases := map[int]int{80: 40, 120: 80, 30: minChartColumns, 1000: maxChartColumns}
	for width, want := range cases {
		if got := chartColumns(width); got != want {
			t.Errorf("chartColumns(%d) = %d, want %d", width, got, want)
		}
	}
}

func TestMilestoneRow(t *testing.T) {
	start := mustDate(t, "2026-10-01")
	end := mustDate(t, "2026-12-31")
	ms := sampleRoadmap().Boards[0].Milestones

	// Bar from column 3 to 21; today (column 5) is hidden under the bar.
	wantBar := "   [" + strings.Repeat("=", 17) + "]" + strings.Repeat(" ", 18)
	if got := milestoneRow(ms[0], start, end, 40, 5); got != wantBar {
		t.Errorf("bar row\n got %q\nwant %q", got, wantBar)
	}

	// Diamond at column 27 (65 * 39 / 91); today stays visible at column 5.
	wantDiamond := strings.Repeat(" ", 5) + "|" + strings.Repeat(" ", 21) + "<>" + strings.Repeat(" ", 11)
	if got := milestoneRow(ms[1], start, end, 40, 5); got != wantDiamond {
		t.Errorf("diamond row\n got %q\nwant %q", got, wantDiamond)
	}

	// A target on the last column shifts the diamond left so both halves fit.
	last := client.Milestone{TargetDate: "2026-12-31"}
	if got := milestoneRow(last, start, end, 40, -1); !strings.HasSuffix(got, "<>") || len(got) != 40 {
		t.Errorf("diamond at the edge = %q, want it to end in <> and stay 40 wide", got)
	}

	// A start date in the same column as the target is drawn as a diamond.
	sameDay := client.Milestone{StartDate: strPtr("2026-11-20"), TargetDate: "2026-11-20"}
	if got := milestoneRow(sameDay, start, end, 40, -1); strings.Contains(got, "[") || !strings.Contains(got, "<>") {
		t.Errorf("start == target = %q, want a diamond", got)
	}

	// A start before the window is clamped to the first column.
	clipped := client.Milestone{StartDate: strPtr("2026-08-01"), TargetDate: "2026-10-10"}
	if got := milestoneRow(clipped, start, end, 40, -1); !strings.HasPrefix(got, "[==]") {
		t.Errorf("clipped bar = %q, want it to start at column 0", got)
	}

	// An unreadable target date draws nothing but today.
	bad := client.Milestone{TargetDate: "soon"}
	if got := milestoneRow(bad, start, end, 40, 5); strings.TrimSpace(got) != "|" {
		t.Errorf("bad target = %q, want only the today marker", got)
	}
}

func TestMonthHeader(t *testing.T) {
	start := mustDate(t, "2026-10-01")
	end := mustDate(t, "2026-12-31")
	labels, ruler := monthHeader(start, end, 40, 5)

	// Months start at columns 0 (Oct), 13 (Nov: 31 * 39 / 91) and 26 (Dec: 61 * 39 / 91).
	wantLabels := "Oct 2026" + strings.Repeat(" ", 5) + "Nov" + strings.Repeat(" ", 10) + "Dec" + strings.Repeat(" ", 11)
	if labels != wantLabels {
		t.Errorf("labels\n got %q\nwant %q", labels, wantLabels)
	}
	wantRuler := "+----|-------+------------+-------------"
	if ruler != wantRuler {
		t.Errorf("ruler\n got %q\nwant %q", ruler, wantRuler)
	}
}

func TestMonthHeaderYearOnJanuaryAndSkipsOverlap(t *testing.T) {
	start := mustDate(t, "2026-11-15")
	end := mustDate(t, "2027-02-28")
	labels, ruler := monthHeader(start, end, 30, -1)
	if !strings.HasPrefix(labels, "Nov 2026") {
		t.Errorf("labels = %q, want the first month with its year", labels)
	}
	if strings.Contains(labels, "Dec") {
		t.Errorf("labels = %q, want Dec left out where it would overlap Nov 2026", labels)
	}
	if !strings.Contains(labels, "Jan 2027") {
		t.Errorf("labels = %q, want January with its year", labels)
	}
	if ruler[0] != '-' {
		t.Errorf("ruler = %q, want no tick at a window start that is not the first of a month", ruler)
	}
}

func TestRender(t *testing.T) {
	out := Render(sampleRoadmap(), 80, mustDate(t, "2026-10-14"))
	lines := strings.Split(strings.TrimRight(out, "\n"), "\n")

	want := []string{
		"Roadmap: Kagi (KAGI)  2026-10-01 .. 2026-12-31",
		"",
		"  Oct 2026     Nov          Dec",
		"  +----|-------+------------+-------------",
		"",
		"KAGI  Kagi",
		"     [" + strings.Repeat("=", 17) + "]" + strings.Repeat(" ", 18) + "  Beta  3/10  ON_TRACK  2026-11-20",
		"       |" + strings.Repeat(" ", 21) + "<>" + strings.Repeat(" ", 11) + "  GA  0/4  AT_RISK  2026-12-05",
		"",
		"KAGIM  Kagi Mobile",
		"  (no milestones)",
		"",
		"  [===] start to target   <> target only   | today",
	}
	if len(lines) != len(want) {
		t.Fatalf("got %d lines, want %d:\n%s", len(lines), len(want), out)
	}
	for i := range want {
		if lines[i] != want[i] {
			t.Errorf("line %d\n got %q\nwant %q", i, lines[i], want[i])
		}
	}
}

func TestRenderDependencies(t *testing.T) {
	resp := sampleRoadmap()
	resp.Boards[0].Milestones[1].DependsOn = []client.MilestoneDependency{
		{MilestoneID: "m1", Name: "Beta", TargetDate: "2026-11-20", Conflict: true},
		{MilestoneID: "m9", Name: "Docs", TargetDate: "2026-10-30"},
	}
	out := Render(resp, 80, mustDate(t, "2026-10-14"))
	lines := strings.Split(strings.TrimRight(out, "\n"), "\n")

	blank := "       |" + strings.Repeat(" ", 34)
	want := []string{
		"       |" + strings.Repeat(" ", 21) + "<>" + strings.Repeat(" ", 11) + "  GA  0/4  AT_RISK  2026-12-05",
		blank + "  ! <- Beta  due 2026-11-20",
		blank + "    <- Docs  due 2026-10-30",
		"",
		"KAGIM  Kagi Mobile",
		"  (no milestones)",
		"",
		"  [===] start to target   <> target only   | today",
		"  <- depends on   ! starts before the dependency is due",
	}
	if len(lines) < len(want) {
		t.Fatalf("got %d lines, want at least %d:\n%s", len(lines), len(want), out)
	}
	got := lines[len(lines)-len(want):]
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("line %d\n got %q\nwant %q", i, got[i], want[i])
		}
	}
}

func TestSortMilestones(t *testing.T) {
	in := []client.Milestone{
		{ID: "m5", Name: "Late", StartDate: strPtr("2026-11-01"), TargetDate: "2026-11-30"},
		{ID: "m4", Name: "Diamond", TargetDate: "2026-10-20"},
		{ID: "m3", Name: "Early", StartDate: strPtr("2026-10-05"), TargetDate: "2026-12-01"},
		{ID: "m2", Name: "Same", StartDate: strPtr("2026-10-20"), TargetDate: "2026-10-25"},
		{ID: "m1", Name: "Same", StartDate: strPtr("2026-10-20"), TargetDate: "2026-10-25"},
		{ID: "m0", Name: "Alpha", StartDate: strPtr("2026-10-20"), TargetDate: "2026-10-25"},
	}
	got := sortMilestones(in)
	want := []string{"m3", "m4", "m0", "m1", "m2", "m5"}
	for i, id := range want {
		if got[i].ID != id {
			t.Fatalf("position %d: got %s, want %s (order %v)", i, got[i].ID, id, milestoneIDs(got))
		}
	}
	if in[0].ID != "m5" {
		t.Errorf("sortMilestones reordered its input: %v", milestoneIDs(in))
	}
}

func TestRenderSortsMilestones(t *testing.T) {
	resp := sampleRoadmap()
	ms := resp.Boards[0].Milestones
	resp.Boards[0].Milestones = []client.Milestone{ms[1], ms[0]}
	out := Render(resp, 80, mustDate(t, "2026-10-14"))
	if strings.Index(out, "Beta") > strings.Index(out, "GA  ") {
		t.Errorf("Beta (starts 2026-10-10) should come before GA (due 2026-12-05):\n%s", out)
	}
}

func milestoneIDs(ms []client.Milestone) []string {
	ids := make([]string, len(ms))
	for i, m := range ms {
		ids[i] = m.ID
	}
	return ids
}

func TestDependencyText(t *testing.T) {
	d := client.MilestoneDependency{Name: "Beta", TargetDate: "2026-11-20"}
	if got, want := dependencyText(d), "  <- Beta  due 2026-11-20"; got != want {
		t.Errorf("dependencyText = %q, want %q", got, want)
	}
	d.Conflict = true
	if got, want := dependencyText(d), "! <- Beta  due 2026-11-20"; got != want {
		t.Errorf("dependencyText (conflict) = %q, want %q", got, want)
	}
}

func TestRenderTodayOutsideWindow(t *testing.T) {
	out := Render(sampleRoadmap(), 80, mustDate(t, "2027-06-01"))
	for _, line := range strings.Split(out, "\n") {
		if strings.HasPrefix(line, "  +") && strings.Contains(line, "|") {
			t.Errorf("ruler %q has a today marker for a day outside the window", line)
		}
	}
}

func TestRenderTruncatesLongNames(t *testing.T) {
	resp := sampleRoadmap()
	resp.Boards[0].Milestones[0].Name = strings.Repeat("Very long milestone name ", 5)
	out := Render(resp, 80, mustDate(t, "2026-10-14"))
	for _, line := range strings.Split(out, "\n") {
		if len([]rune(line)) > 80 {
			t.Errorf("line is %d runes, wider than the 80-column terminal: %q", len([]rune(line)), line)
		}
	}
	if !strings.Contains(out, "...") {
		t.Error("want the long name cut with ...")
	}
}

func TestRenderEmpty(t *testing.T) {
	resp := client.Roadmap{Boards: []client.RoadmapBoard{{Name: "Kagi", Prefix: "KAGI"}}}
	out := Render(resp, 80, mustDate(t, "2026-10-14"))
	if !strings.Contains(out, "No milestones in this window.") {
		t.Errorf("empty roadmap = %q, want the empty message", out)
	}
}

func TestRenderFlagsUnreadableTargetDate(t *testing.T) {
	resp := sampleRoadmap()
	resp.Boards[0].Milestones = append(resp.Boards[0].Milestones,
		client.Milestone{ID: "m3", Name: "Broken", TargetDate: "not-a-date", Health: "ON_TRACK"})
	out := Render(resp, 80, mustDate(t, "2026-10-14"))
	if !strings.Contains(out, "Broken  (unreadable date)") {
		t.Errorf("roadmap = %q, want the unreadable milestone flagged", out)
	}
}

func TestTruncateRunes(t *testing.T) {
	cases := []struct {
		in   string
		n    int
		want string
	}{
		{"short", 10, "short"},
		{"exactly10!", 10, "exactly10!"},
		{"a longer text", 8, "a lon..."},
		{"Größenänderung", 6, "Grö..."},
		{"abc", 2, "ab"},
		{"abc", 0, ""},
	}
	for _, tc := range cases {
		if got := truncateRunes(tc.in, tc.n); got != tc.want {
			t.Errorf("truncateRunes(%q, %d) = %q, want %q", tc.in, tc.n, got, tc.want)
		}
	}
}

// The today marker is the machine-local calendar day, built the way the roadmap command
// builds it. For a viewer west of UTC in the evening, the UTC day is already
// the next one and must not be used.
func TestRoadmapTodayMarkerFollowsTheLocalDay(t *testing.T) {
	newYork := time.FixedZone("UTC-5", -5*60*60)
	now := time.Date(2026, time.November, 4, 22, 0, 0, 0, newYork) // 03:00 on 5 Nov in UTC

	today, err := dates.ParseDate(now.Format(time.DateOnly))
	if err != nil {
		t.Fatalf("cannot resolve today: %v", err)
	}
	if want := mustDate(t, "2026-11-04"); !today.Equal(want) {
		t.Fatalf("today = %v, want the local day %v", today, want)
	}

	resp := sampleRoadmap() // window 2026-10-01 .. 2026-12-31
	start := mustDate(t, "2026-10-01")
	end := mustDate(t, "2026-12-31")
	cols := chartColumns(80)
	localCol := dateColumn(today, start, end, cols)
	utcCol := dateColumn(mustDate(t, "2026-11-05"), start, end, cols)
	if localCol == utcCol {
		t.Fatalf("test window does not separate 4 and 5 Nov: both column %d", localCol)
	}

	out := Render(resp, 80, today)
	lines := strings.Split(out, "\n")
	ruler := lines[3] // title, blank, month labels, ruler
	pos := strings.Index(ruler, "|")
	if pos < 0 {
		t.Fatalf("ruler %q has no today marker", ruler)
	}
	if got := pos - len(indent); got != localCol {
		t.Errorf("today marker at column %d, want %d (local day), not %d (UTC day)", got, localCol, utcCol)
	}
}

// Europe/Amsterdam moves to summer time on 29 Mar 2026, so that local day lasts
// 23 hours. Every day must still land in the column of its calendar-day count,
// and the month ruler must put its '+' on 1 Apr.
func TestRoadmapAcrossDaylightSavingChange(t *testing.T) {
	amsterdam, err := time.LoadLocation("Europe/Amsterdam")
	if err != nil {
		t.Fatalf("cannot load Europe/Amsterdam: %v", err)
	}
	oldLocal := time.Local
	time.Local = amsterdam
	t.Cleanup(func() { time.Local = oldLocal })

	resp := client.Roadmap{
		Boards: []client.RoadmapBoard{{
			ID: "b1", Name: "Kagi", Prefix: "KAGI",
			Milestones: []client.Milestone{
				{ID: "m1", Name: "Beta", StartDate: strPtr("2026-03-01"), TargetDate: "2026-04-30", Health: "ON_TRACK"},
			},
		}},
	}
	start, end, ok := span(resp)
	if !ok {
		t.Fatal("span found no window")
	}
	if want := mustDate(t, "2026-03-01"); !start.Equal(want) || start.Location() != amsterdam {
		t.Fatalf("window start = %v, want %v in Europe/Amsterdam", start, want)
	}
	if want := mustDate(t, "2026-04-30"); !end.Equal(want) || end.Location() != amsterdam {
		t.Fatalf("window end = %v, want %v in Europe/Amsterdam", end, want)
	}

	const cols = 61 // 60 days between the bounds: one column per day
	cases := map[string]int{
		"2026-03-01": 0, "2026-03-28": 27, "2026-03-29": 28, "2026-03-30": 29,
		"2026-03-31": 30, "2026-04-01": 31, "2026-04-15": 45, "2026-04-30": 60,
	}
	for day, want := range cases {
		if got := dateColumn(mustDate(t, day), start, end, cols); got != want {
			t.Errorf("dateColumn(%s) = %d, want %d", day, got, want)
		}
	}

	_, ruler := monthHeader(start, end, cols, -1)
	if got := []rune(ruler); got[0] != '+' || got[31] != '+' || strings.Count(ruler, "+") != 2 {
		t.Errorf("ruler %q: want '+' at columns 0 and 31 only", ruler)
	}
}
