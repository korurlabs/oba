package project

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads the projects of board boardID.
func List(c *client.Client, boardID string) ([]client.Project, error) {
	return c.ListProjects(boardID)
}

// PrintList writes the projects as a NAME, COLOR, ID table. Names are typed by users, so every
// cell is put on one line with terminal controls removed.
func PrintList(out io.Writer, projects []client.Project) error {
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tCOLOR\tID")
	for _, p := range projects {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\n", guardrail.OneLine(p.Name), guardrail.OneLine(color(p.Color)), guardrail.OneLine(p.ID))
	}
	return w.Flush()
}
