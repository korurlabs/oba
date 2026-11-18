package contact

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads every contact of relation relationID.
func List(c *client.Client, relationID string) ([]client.Contact, error) {
	return c.ListContacts(relationID)
}

// PrintList writes the contacts as a table: name, kind, whether the contact is the relation's
// main contact of its kind, role, email, email type, phone, source page, id. Every cell is
// sanitized onto one line, since contacts can come from web pages.
func PrintList(out io.Writer, contacts []client.Contact) error {
	if len(contacts) == 0 {
		_, _ = fmt.Fprintln(out, "No contacts.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tKIND\tMAIN\tROLE\tEMAIL\tEMAIL TYPE\tPHONE\tSOURCE\tID")
	for _, ct := range contacts {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", orDash(guardrail.OneLine(ct.Name)), kindCell(ct.Kind),
			mainCell(ct.MainPersonContact || ct.MainBusinessContact), optional(ct.Role),
			optional(ct.Email), optional(ct.EmailType), optional(ct.Phone), optional(ct.SourceURL), orDash(guardrail.OneLine(ct.ID)))
	}
	return w.Flush()
}

// kindCell is the contact's kind in lower case (person, business), or "-" when the API sent none.
func kindCell(kind string) string {
	return orDash(strings.ToLower(guardrail.OneLine(kind)))
}

// mainCell marks the relation's main contact of each kind with "yes" and every other contact
// with "-".
func mainCell(main bool) string {
	if main {
		return "yes"
	}
	return "-"
}
