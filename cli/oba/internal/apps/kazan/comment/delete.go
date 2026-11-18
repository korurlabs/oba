package comment

import (
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes comment commentID of ticket t.
func Delete(c *client.Client, t ticket.Target, commentID string) error {
	return c.DeleteComment(t.BoardID, t.ID, commentID)
}
