package roadmap

import (
	"cmp"
	"fmt"
	"math"
	"slices"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Render draws the roadmap as plain text for a terminal of the given width: a title, a header of
// month ticks, then one lane per board with one row per milestone. today is the viewer's day at
// local midnight. It writes no colour codes, so its output is the same in a terminal and a pipe.
func Render(r client.Roadmap, width int, today time.Time) string {
	var sb strings.Builder

	title := "Roadmap"
	if len(r.Boards) > 0 {
		title = fmt.Sprintf("Roadmap: %s (%s)", guardrail.OneLine(r.Boards[0].Name), guardrail.OneLine(r.Boards[0].Prefix))
	}

	start, end, ok := span(r)
	if !ok {
		sb.WriteString(title + "\n\nNo milestones in this window.\n")
		return sb.String()
	}

	cols := chartColumns(width)
	_, _ = fmt.Fprintf(&sb, "%s  %s .. %s\n\n", title, start.Format(time.DateOnly), end.Format(time.DateOnly))

	todayCol := -1
	if !today.Before(start) && !today.After(end) {
		todayCol = dateColumn(today, start, end, cols)
	}

	labels, ruler := monthHeader(start, end, cols, todayCol)
	sb.WriteString(indent + strings.TrimRight(labels, " ") + "\n")
	sb.WriteString(indent + ruler + "\n")

	textWidth := width - 2*len(indent) - cols
	if textWidth < minChartColumns {
		textWidth = minChartColumns
	}
	hasDependencies := false
	for _, b := range r.Boards {
		sb.WriteString("\n")
		_, _ = fmt.Fprintf(&sb, "%s  %s\n", guardrail.OneLine(b.Prefix), guardrail.OneLine(b.Name))
		if len(b.Milestones) == 0 {
			sb.WriteString(indent + "(no milestones)\n")
			continue
		}
		for _, m := range sortMilestones(b.Milestones) {
			row := milestoneRow(m, start, end, cols, todayCol)
			name := guardrail.OneLine(m.Name)
			text := fmt.Sprintf("%s  %d/%d  %s  %s", name, m.DoneCount, m.TotalCount, m.Health, m.TargetDate)
			if _, err := dates.ParseDate(m.TargetDate); err != nil {
				// Kept short so the note survives truncation in the text column.
				text = fmt.Sprintf("%s  (unreadable date)  %d/%d  %s  %s", name, m.DoneCount, m.TotalCount, m.Health, m.TargetDate)
			}
			sb.WriteString(indent + row + indent + truncateRunes(text, textWidth) + "\n")
			for _, d := range m.DependsOn {
				hasDependencies = true
				sb.WriteString(indent + dependencyRow(cols, todayCol) + indent + truncateRunes(dependencyText(d), textWidth) + "\n")
			}
		}
	}

	sb.WriteString("\n" + indent + "[===] start to target   <> target only   | today\n")
	if hasDependencies {
		sb.WriteString(indent + "<- depends on   ! starts before the dependency is due\n")
	}
	return sb.String()
}

// sortMilestones returns the milestones by start date (the target date when there is no start),
// then target date, then name, then id, so the rows read left to right like the web roadmap. The
// input slice is left unchanged.
func sortMilestones(milestones []client.Milestone) []client.Milestone {
	sorted := slices.Clone(milestones)
	slices.SortFunc(sorted, func(a client.Milestone, b client.Milestone) int {
		return cmp.Or(
			strings.Compare(firstDate(a), firstDate(b)),
			strings.Compare(a.TargetDate, b.TargetDate),
			strings.Compare(a.Name, b.Name),
			strings.Compare(a.ID, b.ID),
		)
	})
	return sorted
}

// firstDate is the day a milestone's row begins at: its start date, or its target date when it
// has no start. ISO days compare correctly as strings.
func firstDate(m client.Milestone) string {
	if m.StartDate != nil {
		return *m.StartDate
	}
	return m.TargetDate
}

// chartColumns is the width of the timeline for a terminal of the given width.
func chartColumns(width int) int {
	return min(max(width-2*len(indent)-textReserve, minChartColumns), maxChartColumns)
}

// dateColumn maps a day to its column on a timeline of cols columns from start to end. Days
// outside the window are clamped to its edges. The days are local midnights, and a local day
// lasts 23 or 25 hours across a daylight saving change, so day counts are rounded, not
// truncated: a truncated count would put every day after the change one column early.
func dateColumn(d time.Time, start time.Time, end time.Time, cols int) int {
	if !d.After(start) {
		return 0
	}
	if !d.Before(end) {
		return cols - 1
	}
	totalDays := int(math.Round(end.Sub(start).Hours() / 24))
	if totalDays <= 0 {
		return 0
	}
	days := int(math.Round(d.Sub(start).Hours() / 24))
	return days * (cols - 1) / totalDays
}

// monthHeader builds the two header lines: month names, and a ruler with a + at the first day of
// each month and a | at today. A month name that would overlap the previous one is left out.
func monthHeader(start time.Time, end time.Time, cols int, todayCol int) (string, string) {
	labels := []rune(strings.Repeat(" ", cols))
	ruler := []rune(strings.Repeat("-", cols))

	nextFree := 0
	first := true
	for m := time.Date(start.Year(), start.Month(), 1, 0, 0, 0, 0, start.Location()); !m.After(end); m = m.AddDate(0, 1, 0) {
		at := m
		if at.Before(start) {
			at = start
		}
		col := dateColumn(at, start, end, cols)
		if !m.Before(start) {
			ruler[col] = '+'
		}

		name := m.Format("Jan")
		if first || m.Month() == time.January {
			name = m.Format("Jan 2006")
		}
		if col >= nextFree && col+len(name) <= cols {
			copy(labels[col:], []rune(name))
			nextFree = col + len(name) + 1
		}
		first = false
	}

	if todayCol >= 0 {
		ruler[todayCol] = '|'
	}
	return string(labels), string(ruler)
}

// milestoneRow draws one milestone on the timeline: [===] from its start date to its target
// date, or <> at the target date when it has no start date or the span fits in one column.
func milestoneRow(m client.Milestone, start time.Time, end time.Time, cols int, todayCol int) string {
	row := []rune(strings.Repeat(" ", cols))
	if todayCol >= 0 {
		row[todayCol] = '|'
	}

	target, err := dates.ParseDate(m.TargetDate)
	if err != nil {
		return string(row)
	}
	targetCol := dateColumn(target, start, end, cols)

	if m.StartDate != nil {
		if s, sErr := dates.ParseDate(*m.StartDate); sErr == nil {
			startCol := dateColumn(s, start, end, cols)
			if startCol < targetCol {
				row[startCol] = '['
				for i := startCol + 1; i < targetCol; i++ {
					row[i] = '='
				}
				row[targetCol] = ']'
				return string(row)
			}
		}
	}

	if targetCol > cols-2 {
		targetCol = cols - 2
	}
	row[targetCol] = '<'
	row[targetCol+1] = '>'
	return string(row)
}

// dependencyRow is the chart part of a dependency line: blank but for the today marker, so the
// today line stays unbroken.
func dependencyRow(cols int, todayCol int) string {
	row := []rune(strings.Repeat(" ", cols))
	if todayCol >= 0 {
		row[todayCol] = '|'
	}
	return string(row)
}

// dependencyText names a dependency under its dependent's row, with ! first when the dependent
// starts before the dependency's target date.
func dependencyText(d client.MilestoneDependency) string {
	marker := "  "
	if d.Conflict {
		marker = "! "
	}
	return fmt.Sprintf("%s<- %s  due %s", marker, guardrail.OneLine(d.Name), d.TargetDate)
}

// truncateRunes shortens s to at most n runes, ending in "..." when cut.
func truncateRunes(s string, n int) string {
	r := []rune(s)
	if n <= 0 {
		return ""
	}
	if len(r) <= n {
		return s
	}
	if n <= 3 {
		return string(r[:n])
	}
	return string(r[:n-3]) + "..."
}
