package comment

import (
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Add adds a comment with content to ticket t.
func Add(c *client.Client, t ticket.Target, content string) (client.Comment, error) {
	return c.AddComment(t.BoardID, t.ID, content)
}
