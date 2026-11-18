package invite

import (
	"fmt"
	"io"
	"text/tabwriter"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// List reads the pending invitations of the client's organization.
func List(c *client.Client) ([]client.Invitation, error) {
	return c.ListInvitations()
}

// PrintList writes the invitations as a table: e-mail, role, inviter, expiry day in loc, id.
// Every cell is sanitized onto one line, since the e-mails are typed by users.
func PrintList(out io.Writer, invs []client.Invitation, loc *time.Location) error {
	if len(invs) == 0 {
		_, _ = fmt.Fprintln(out, "No pending invitations.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "EMAIL\tROLE\tINVITED BY\tEXPIRES\tID")
	for _, inv := range invs {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n", cell(inv.Email), cell(inv.Role), cell(inv.InvitedByEmail),
			cell(localDay(inv.ExpiresAt, loc)), cell(inv.ID))
	}
	return w.Flush()
}
