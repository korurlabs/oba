package project

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Delete deletes project id of board boardID.
func Delete(c *client.Client, boardID, id string) error {
	id, err := checkID(id)
	if err != nil {
		return err
	}
	return c.DeleteProject(boardID, id)
}
