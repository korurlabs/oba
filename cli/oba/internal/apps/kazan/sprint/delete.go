package sprint

import (
	"errors"
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// DeleteOptions says where the tickets of a deleted sprint go: TicketsTo is backlog (also when
// empty), sprint or delete, on this board; TargetSprint names the sprint for sprint. The API
// refuses to delete a sprint that still holds tickets without an action, so one is always sent.
// Yes confirms a delete of the tickets.
type DeleteOptions struct {
	TicketsTo    string
	TargetSprint string
	Yes          bool
}

// Check refuses a combination the API would refuse or that deletes tickets without Yes. It
// makes no call, so a command can run it before it asks for confirmation.
func (o DeleteOptions) Check() (string, error) {
	action, err := ticketAction(o.TicketsTo)
	if err != nil {
		return "", err
	}
	if err := checkTargetSprint(action, o.TargetSprint); err != nil {
		return "", err
	}
	if action == client.RelocationDelete && !o.Yes {
		return "", errors.New("--tickets-to delete deletes every ticket of the sprint; pass --yes to confirm")
	}
	return action, nil
}

// Delete deletes sprint s of board boardID, found with Find, and moves or deletes its tickets as
// opts says.
func Delete(c *client.Client, boardID string, s client.Sprint, opts DeleteOptions) error {
	action, err := opts.Check()
	if err != nil {
		return err
	}
	query := url.Values{}
	query.Set("ticketAction", action)
	if action == client.RelocationMoveToSprint {
		target, err := Find(c, boardID, opts.TargetSprint)
		if err != nil {
			return fmt.Errorf("failed to resolve target sprint: %w", err)
		}
		if strings.EqualFold(target.ID, s.ID) {
			return errors.New("--target-sprint must be another sprint than the one being deleted")
		}
		query.Set("targetSprintId", target.ID)
	}
	return c.DeleteSprint(boardID, s.ID, query)
}
