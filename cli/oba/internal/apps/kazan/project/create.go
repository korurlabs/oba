package project

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// CreateInput is a new project: a name and, when given, a color.
type CreateInput struct {
	Name  string
	Color *string
}

// Create creates the project on board boardID and returns it. A missing name is refused before
// the call.
func Create(c *client.Client, boardID string, in CreateInput) (client.Project, error) {
	name := strings.TrimSpace(in.Name)
	if name == "" {
		return client.Project{}, errors.New("--name is required")
	}
	return c.CreateProject(boardID, client.ProjectCreateRequest{Name: name, Color: in.Color})
}
