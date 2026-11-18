package contact

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes contact contactID of relation relationID.
func Delete(c *client.Client, relationID, contactID string) error {
	return c.DeleteContact(relationID, contactID)
}
