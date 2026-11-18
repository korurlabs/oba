package sprint

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// UpdateInput holds the fields to change; a nil field keeps its value. StartDate and EndDate are
// RFC 3339 instants.
type UpdateInput struct {
	Name       *string
	SeriesName *string
	Goal       *string
	StartDate  *string
	EndDate    *string
}

func (in UpdateInput) empty() bool {
	return in.Name == nil && in.SeriesName == nil && in.Goal == nil && in.StartDate == nil && in.EndDate == nil
}

// Update changes the given fields of the sprint of board boardID that name names and returns it.
// With no field given it fails with terminal.ErrNothingToUpdate before any call.
func Update(c *client.Client, boardID, name string, in UpdateInput) (client.Sprint, error) {
	if in.empty() {
		return client.Sprint{}, terminal.ErrNothingToUpdate
	}
	found, err := Find(c, boardID, name)
	if err != nil {
		return client.Sprint{}, err
	}
	return c.UpdateSprint(boardID, found.ID, client.SprintUpdateRequest{
		Name: in.Name, SeriesName: in.SeriesName, Goal: in.Goal, StartDate: in.StartDate, EndDate: in.EndDate,
	})
}
