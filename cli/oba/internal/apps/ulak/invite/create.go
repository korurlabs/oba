package invite

import (
	"fmt"
	"io"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Create invites email with role to the client's organization. role is one of
// role.ParseRole's values; the API checks the rest (not the caller, not a member, no pending
// invitation, a valid e-mail).
func Create(c *client.Client, email, role string) (client.Invitation, error) {
	return c.CreateInvitation(email, role)
}

// PrintCreated tells who was invited where, and until which day of loc the invitation holds.
func PrintCreated(out io.Writer, inv client.Invitation, orgName string, loc *time.Location) {
	msg := fmt.Sprintf("Invited %s as %s to %s. They join when they accept in Oba",
		guardrail.OneLine(inv.Email), guardrail.OneLine(inv.Role), guardrail.OneLine(orgName))
	if day := localDay(inv.ExpiresAt, loc); day != "" {
		msg += "; the invitation expires " + day
	}
	_, _ = fmt.Fprintln(out, msg+".")
}
