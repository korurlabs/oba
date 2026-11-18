package backlog

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Get reads the backlog of board boardID.
func Get(c *client.Client, boardID string) (client.Backlog, error) {
	return c.GetBacklog(boardID)
}

// Print writes the backlog tickets as a KEY, TITLE, STATUS, PRIORITY, TYPE table, or "Backlog is
// empty." when it holds none. Titles are typed by users, so every cell is put on one line with
// terminal controls removed.
func Print(out io.Writer, b client.Backlog) error {
	if len(b.Tickets) == 0 {
		_, _ = fmt.Fprintln(out, "Backlog is empty.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "KEY\tTITLE\tSTATUS\tPRIORITY\tTYPE")
	for _, t := range b.Tickets {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n", guardrail.OneLine(t.Key), guardrail.OneLine(t.Title),
			guardrail.OneLine(t.Status), guardrail.OneLine(t.Priority), guardrail.OneLine(t.Type))
	}
	return w.Flush()
}
