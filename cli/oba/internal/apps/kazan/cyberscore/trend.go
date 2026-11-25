package cyberscore

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// CheckDays refuses a --days value outside the window the API serves, before any call.
func CheckDays(days int) error {
	if days < minTrendDays || days > maxTrendDays {
		return fmt.Errorf("--days must be between %d and %d, got %d", minTrendDays, maxTrendDays, days)
	}
	return nil
}

// Trend reads one score per day for the last days days, oldest first; 0 takes the API's default
// window.
func Trend(c *client.Client, days int) ([]client.CyberscoreTrendPoint, error) {
	if days != 0 {
		if err := CheckDays(days); err != nil {
			return nil, err
		}
	}
	return c.GetCyberscoreTrend(days)
}

// PrintTrend writes the trend as a table, or "No trend yet." when it is empty.
func PrintTrend(out io.Writer, points []client.CyberscoreTrendPoint) error {
	if len(points) == 0 {
		_, _ = fmt.Fprintln(out, "No trend yet.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "DATE\tSCORE\tLEVEL")
	for _, p := range points {
		_, _ = fmt.Fprintf(w, "%s\t%d%%\t%s\n", guardrail.OneLine(p.Date), p.ScorePercentage, guardrail.OneLine(p.ScoreLevel))
	}
	return w.Flush()
}
