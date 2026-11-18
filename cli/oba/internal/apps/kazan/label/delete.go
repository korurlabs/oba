package label

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes label labelID of board boardID.
func Delete(c *client.Client, boardID, labelID string) error {
	return c.DeleteLabel(boardID, labelID)
}
