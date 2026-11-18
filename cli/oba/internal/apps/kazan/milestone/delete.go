package milestone

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Delete deletes milestone m, found with Resolve from board boardID, and detaches its tickets.
func Delete(c *client.Client, boardID string, m client.Milestone) error {
	return c.DeleteMilestone(owner(m, boardID), m.ID)
}
