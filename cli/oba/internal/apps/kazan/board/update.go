package board

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// UpdateInput holds the fields to change; a nil field keeps its value.
type UpdateInput struct {
	Name        *string
	Description *string
	Key         *string
	Color       *string
	Icon        *string
}

func (in UpdateInput) empty() bool {
	return in.Name == nil && in.Description == nil && in.Key == nil && in.Color == nil && in.Icon == nil
}

// Update changes the given fields of the board ref names and returns the board. Nothing given
// is refused before any call.
func Update(c *client.Client, ref string, in UpdateInput) (client.Board, error) {
	if in.empty() {
		return client.Board{}, terminal.ErrNothingToUpdate
	}
	named, err := Resolve(c, ref)
	if err != nil {
		return client.Board{}, err
	}
	return c.UpdateBoard(named.ID, client.BoardUpdateRequest{
		Name: in.Name, Description: in.Description, Prefix: in.Key, Color: in.Color, Icon: in.Icon,
	})
}
