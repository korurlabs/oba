package sprint

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ErrNoName refuses a create without a name before any call.
var ErrNoName = errors.New("--name is required")

// CreateInput is a new sprint. StartDate and EndDate are RFC 3339 instants or empty;
// ChildBoardIDs are the descendant boards the sprint is also linked to.
type CreateInput struct {
	Name          string
	Goal          *string
	StartDate     string
	EndDate       string
	ChildBoardIDs []string
}

// Create creates a sprint on board boardID and returns it.
func Create(c *client.Client, boardID string, in CreateInput) (client.Sprint, error) {
	if strings.TrimSpace(in.Name) == "" {
		return client.Sprint{}, ErrNoName
	}
	req := client.SprintCreateRequest{Name: in.Name, Goal: in.Goal, ChildBoardIDs: in.ChildBoardIDs}
	if in.StartDate != "" {
		req.StartDate = &in.StartDate
	}
	if in.EndDate != "" {
		req.EndDate = &in.EndDate
	}
	return c.CreateSprint(boardID, req)
}
