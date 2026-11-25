package cyberscore

import (
	"fmt"
	"io"
	"slices"
	"sort"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// unscoredLevel is the level of a score no check with a scorable outcome stands behind.
const unscoredLevel = "UNKNOWN"

// Show reads the live score of the organization.
func Show(c *client.Client) (client.Cyberscore, error) {
	return c.GetCyberscore()
}

// PrintScore writes the score, its level, the number of scorable checks, when it was calculated
// and one line per category, in Categories order with unknown categories after them; a score not
// calculated yet is one line that says so.
func PrintScore(out io.Writer, s client.Cyberscore) error {
	if s.ScoreLevel == unscoredLevel {
		_, _ = fmt.Fprintln(out, "Not scored yet: no check has a scorable outcome.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintf(w, "Score:\t%d%% (%s)\n", s.ScorePercentage, guardrail.OneLine(s.ScoreLevel))
	_, _ = fmt.Fprintf(w, "Checks:\t%d scorable\n", s.ScorableCheckCount)
	if s.LastCalculatedAt != nil && *s.LastCalculatedAt != "" {
		_, _ = fmt.Fprintf(w, "Calculated:\t%s\n", dates.FormatInstant(*s.LastCalculatedAt))
	}
	if err := w.Flush(); err != nil {
		return err
	}
	if len(s.BreakdownByCategory) == 0 {
		return nil
	}
	_, _ = fmt.Fprintln(out, "Breakdown:")
	w = tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	for _, category := range breakdownOrder(s.BreakdownByCategory) {
		_, _ = fmt.Fprintf(w, "  %s\t%d%%\n", guardrail.OneLine(category), s.BreakdownByCategory[category])
	}
	return w.Flush()
}

// breakdownOrder lists the categories of breakdown in Categories order, then the ones this CLI
// does not know yet, sorted, so that a category added to the API still shows.
func breakdownOrder(breakdown map[string]int) []string {
	order := make([]string, 0, len(breakdown))
	for _, category := range Categories {
		if _, ok := breakdown[category]; ok {
			order = append(order, category)
		}
	}
	var unknown []string
	for category := range breakdown {
		if !slices.Contains(Categories, category) {
			unknown = append(unknown, category)
		}
	}
	sort.Strings(unknown)
	return append(order, unknown...)
}
