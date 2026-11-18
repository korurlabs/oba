package ticket

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes the resolved ticket; it can be restored with its id.
func Delete(c *client.Client, target Target) error {
	if err := c.DeleteTicket(target.BoardID, target.ID); err != nil {
		return fmt.Errorf("failed to delete ticket: %w", err)
	}
	return nil
}
