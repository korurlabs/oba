package find

import (
	"fmt"
	"io"
	"strconv"
	"strings"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// ShowCandidates prints one row per candidate: number, name, website host and summary, each
// column padded to its widest value. A candidate that matches a known relation shows "already
// in Otak as <name>" in place of its summary and cannot be picked; ShowCandidates returns the
// numbers (1-based) of those. Under each candidate that can be picked go its contact lines (see
// contact.ShowFound). Every value is sanitized onto one line, since it comes from the web.
func ShowCandidates(out io.Writer, candidates []Candidate, known KnownCompanies) map[int]bool {
	skipped := make(map[int]bool)
	rows := make([][3]string, 0, len(candidates))
	var nameWidth, hostWidth int
	for i, candidate := range candidates {
		summary := cut(guardrail.OneLine(candidate.Summary), SummaryMaxLength)
		if existing, ok := known.Match(candidate.Name, candidate.Website); ok {
			skipped[i+1] = true
			summary = "already in Otak as " + guardrail.OneLine(existing)
		}
		row := [3]string{orDash(guardrail.OneLine(candidate.Name)), orDash(guardrail.OneLine(relation.WebsiteHost(candidate.Website))), summary}
		nameWidth = max(nameWidth, utf8.RuneCountInString(row[0]))
		hostWidth = max(hostWidth, utf8.RuneCountInString(row[1]))
		rows = append(rows, row)
	}
	numberWidth := len(strconv.Itoa(len(candidates)))
	for i, row := range rows {
		line := fmt.Sprintf("  %*d. %s%s%s%s%s", numberWidth, i+1,
			row[0], pad(row[0], nameWidth), row[1], pad(row[1], hostWidth), row[2])
		_, _ = fmt.Fprintln(out, strings.TrimRight(line, " "))
		if !skipped[i+1] {
			contact.ShowFound(out, candidates[i].Contacts)
		}
	}
	return skipped
}

// pad is the spaces that fill cell to width, plus the two that separate the columns.
func pad(cell string, width int) string {
	return strings.Repeat(" ", width-utf8.RuneCountInString(cell)+2)
}

// cut shortens value to at most limit runes, since Claude Code may ignore a schema's maxLength.
func cut(value string, limit int) string {
	runes := []rune(value)
	if len(runes) <= limit {
		return value
	}
	return string(runes[:limit])
}

func orDash(value string) string {
	if value == "" {
		return "-"
	}
	return value
}
