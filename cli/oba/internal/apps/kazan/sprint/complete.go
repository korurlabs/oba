package sprint

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// CompleteOptions says where the unfinished tickets of a completed sprint go. TicketsTo is
// backlog (also when empty), sprint or delete; TargetBoard, a board name, prefix or id, receives
// moved tickets instead of this board; TargetSprint is a sprint of the receiving board. Yes
// confirms a delete.
type CompleteOptions struct {
	TicketsTo    string
	TargetSprint string
	TargetBoard  string
	Yes          bool
}

// Check maps TicketsTo to the API's action and refuses a combination the API would refuse or
// that deletes without Yes. It makes no call.
func (o CompleteOptions) Check() (string, error) {
	action, err := ticketAction(o.TicketsTo)
	if err != nil {
		return "", err
	}
	if err := checkTargetSprint(action, o.TargetSprint); err != nil {
		return "", err
	}
	if action == client.RelocationDelete {
		if strings.TrimSpace(o.TargetBoard) != "" {
			return "", errors.New("--target-board is not valid with --tickets-to delete")
		}
		if !o.Yes {
			return "", errors.New("--tickets-to delete deletes every unfinished ticket of the sprint; pass --yes to confirm")
		}
	}
	return action, nil
}

// Complete completes the sprint of board boardID that name names and returns it. The options are
// checked before any call; boards resolves TargetBoard.
func Complete(c *client.Client, boardID, name string, opts CompleteOptions, boards BoardResolver) (client.Sprint, error) {
	action, err := opts.Check()
	if err != nil {
		return client.Sprint{}, err
	}
	found, err := Find(c, boardID, name)
	if err != nil {
		return client.Sprint{}, err
	}
	req := client.SprintCompleteRequest{TicketAction: action}
	receiving := boardID
	if ref := strings.TrimSpace(opts.TargetBoard); ref != "" {
		id, err := boards(ref)
		if err != nil {
			return client.Sprint{}, err
		}
		req.TargetBoardID = &id
		receiving = id
	}
	if action == client.RelocationMoveToSprint {
		target, err := Find(c, receiving, opts.TargetSprint)
		if err != nil {
			return client.Sprint{}, fmt.Errorf("failed to resolve target sprint: %w", err)
		}
		req.TargetSprintID = &target.ID
	}
	return found, c.CompleteSprint(boardID, found.ID, req)
}

// ticketAction maps a --tickets-to value of complete or delete to the API's action.
func ticketAction(ticketsTo string) (string, error) {
	switch strings.ToLower(strings.TrimSpace(ticketsTo)) {
	case "", ticketsToBacklog:
		return client.RelocationMoveToBacklog, nil
	case ticketsToSprint:
		return client.RelocationMoveToSprint, nil
	case ticketsToDelete:
		return client.RelocationDelete, nil
	default:
		return "", fmt.Errorf("invalid --tickets-to %q: use backlog, sprint or delete", ticketsTo)
	}
}

// checkTargetSprint refuses a move to a sprint without --target-sprint and --target-sprint with
// any other action.
func checkTargetSprint(action, targetSprint string) error {
	hasTarget := strings.TrimSpace(targetSprint) != ""
	if action == client.RelocationMoveToSprint && !hasTarget {
		return errors.New("--tickets-to sprint needs --target-sprint")
	}
	if action != client.RelocationMoveToSprint && hasTarget {
		return errors.New("--target-sprint is only valid with --tickets-to sprint")
	}
	return nil
}
