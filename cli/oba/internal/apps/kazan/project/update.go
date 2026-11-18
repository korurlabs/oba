package project

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// UpdateInput holds the fields to change; a nil field keeps its value.
type UpdateInput struct {
	Name  *string
	Color *string
}

// Update changes the given fields of project id of board boardID and returns the project. With no
// field given it fails with terminal.ErrNothingToUpdate before any call.
func Update(c *client.Client, boardID, id string, in UpdateInput) (client.Project, error) {
	id, err := checkID(id)
	if err != nil {
		return client.Project{}, err
	}
	if in.Name == nil && in.Color == nil {
		return client.Project{}, terminal.ErrNothingToUpdate
	}
	return c.UpdateProject(boardID, id, client.ProjectUpdateRequest{Name: in.Name, Color: in.Color})
}
