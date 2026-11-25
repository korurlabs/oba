package cyberscore

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// ListChecks reads the checks of the organization in the API's order (category, sort order,
// name), only those of status when it is given.
func ListChecks(c *client.Client, status string) ([]client.CyberscoreCheck, error) {
	if strings.TrimSpace(status) != "" {
		parsed, err := ParseStatus(status)
		if err != nil {
			return nil, err
		}
		status = parsed
	}
	return c.ListCyberscoreChecks(status)
}

// PrintChecks writes the checks as a table, or "No checks found." when there are none.
func PrintChecks(out io.Writer, checks []client.CyberscoreCheck) error {
	if len(checks) == 0 {
		_, _ = fmt.Fprintln(out, "No checks found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "ID\tNAME\tCATEGORY\tWEIGHT\tOUTCOME\tSTATUS")
	for _, ch := range checks {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%d\t%s\t%s\n", ch.ID, guardrail.OneLine(ch.Name), orDash(ch.Category),
			ch.Weight, guardrail.OneLine(ch.Outcome), guardrail.OneLine(ch.Status))
	}
	return w.Flush()
}
