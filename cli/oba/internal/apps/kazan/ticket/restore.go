package ticket

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// Restore restores the deleted ticket with id ticketID on the selected board. A key is looked up
// among live tickets only, so a deleted ticket cannot be found by its key.
func Restore(c *client.Client, r *Resolver, ticketID string) error {
	if !userinput.IsID(ticketID) {
		return fmt.Errorf("ticket restore takes the ticket's id and --board: a deleted ticket cannot be found by its key %q", ticketID)
	}
	target, err := r.Ticket(ticketID)
	if err != nil {
		return err
	}
	if err := c.RestoreTicket(target.BoardID, target.ID); err != nil {
		return fmt.Errorf("failed to restore ticket: %w", err)
	}
	return nil
}
