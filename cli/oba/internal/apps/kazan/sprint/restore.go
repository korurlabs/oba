package sprint

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Restore restores deleted sprint id of board boardID. A deleted sprint is no longer listed, so
// it is named by its id.
func Restore(c *client.Client, boardID, id string) error {
	return c.RestoreSprint(boardID, id)
}
