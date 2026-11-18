package member

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads the members of board boardID.
func List(c *client.Client, boardID string) ([]client.BoardMember, error) {
	return c.ListBoardMembers(boardID)
}

// PrintList writes the members as a NAME, EMAIL, ROLE table. Names are typed by users, so every
// cell is put on one line with terminal controls removed.
func PrintList(out io.Writer, members []client.BoardMember) error {
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tEMAIL\tROLE")
	for _, m := range members {
		name := strings.TrimSpace(guardrail.OneLine(m.FirstName) + " " + guardrail.OneLine(m.LastName))
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\n", name, guardrail.OneLine(m.Email), guardrail.OneLine(m.Role))
	}
	return w.Flush()
}
