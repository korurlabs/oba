package label

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// Update changes the given fields of label labelID on board boardID; a nil field keeps its
// value. With no field given it fails with terminal.ErrNothingToUpdate before any call.
func Update(c *client.Client, boardID, labelID string, name, color *string) (client.Label, error) {
	if name == nil && color == nil {
		return client.Label{}, terminal.ErrNothingToUpdate
	}
	return c.UpdateLabel(boardID, labelID, client.LabelUpdateRequest{Name: name, Color: color})
}
