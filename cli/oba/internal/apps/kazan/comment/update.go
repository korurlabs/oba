package comment

import (
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Update replaces the content of comment commentID of ticket t.
func Update(c *client.Client, t ticket.Target, commentID, content string) (client.Comment, error) {
	return c.UpdateComment(t.BoardID, t.ID, commentID, content)
}
