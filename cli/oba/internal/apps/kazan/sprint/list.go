package sprint

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads the sprints of board boardID.
func List(c *client.Client, boardID string) ([]client.Sprint, error) {
	return c.ListSprints(boardID)
}

// PrintList writes the sprints as a NAME, STATUS, START, END table, or "No sprints found." when
// there are none. The dates are shown as the API sent them.
func PrintList(out io.Writer, sprints []client.Sprint) error {
	if len(sprints) == 0 {
		_, _ = fmt.Fprintln(out, "No sprints found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tSTATUS\tSTART\tEND")
	for _, s := range sprints {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", guardrail.OneLine(s.Name), s.Status, orDash(s.StartDate), orDash(s.EndDate))
	}
	return w.Flush()
}
