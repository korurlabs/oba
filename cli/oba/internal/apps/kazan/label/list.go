package label

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads the labels of board boardID.
func List(c *client.Client, boardID string) ([]client.Label, error) {
	return c.ListLabels(boardID)
}

// PrintList writes the labels as one table: name, color and id, the id that update and delete
// take.
func PrintList(out io.Writer, labels []client.Label) error {
	if len(labels) == 0 {
		_, _ = fmt.Fprintln(out, "No labels found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tCOLOR\tID")
	for _, l := range labels {
		name := l.Name
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\n", orDash(&name), orDash(l.Color), guardrail.OneLine(l.ID))
	}
	return w.Flush()
}
