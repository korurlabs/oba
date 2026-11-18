// Package organization prints the signed-in user's organizations and finds an organization or a
// member of one by what a user typed, for every command that needs it, so that the lists and
// lookups never drift apart.
package organization

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintList writes orgs as a NAME, ROLE, STATUS, ID table whose first column marks the
// organization with currentID by "*", or "You belong to no organization." when there are none.
// Every cell is cut to one line without control characters: a name is typed by any user who
// creates an organization, and a newline or escape sequence in it must not forge a row or
// drive the terminal.
func PrintList(out io.Writer, orgs []client.Organization, currentID string) error {
	if len(orgs) == 0 {
		_, _ = fmt.Fprintln(out, "You belong to no organization.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "\tNAME\tROLE\tSTATUS\tID")
	for _, org := range orgs {
		marker := ""
		if org.ID == currentID {
			marker = "*"
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n", marker, guardrail.OneLine(org.Name), guardrail.OneLine(org.Role),
			guardrail.OneLine(org.Status), guardrail.OneLine(org.ID))
	}
	return w.Flush()
}
