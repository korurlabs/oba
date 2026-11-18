package label

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Create creates a label named name on board boardID; a nil color leaves it without one.
func Create(c *client.Client, boardID, name string, color *string) (client.Label, error) {
	if strings.TrimSpace(name) == "" {
		return client.Label{}, errors.New("--name is required")
	}
	return c.CreateLabel(boardID, client.LabelCreateRequest{Name: name, Color: color})
}
