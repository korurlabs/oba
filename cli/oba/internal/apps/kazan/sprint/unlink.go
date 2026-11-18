package sprint

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// UnlinkOptions says what happens to a sprint's tickets when it is unlinked from a board.
// TicketsTo is backlog (also when empty) or sprint, both on this board; TargetSprint names the
// sprint for sprint. DeleteTickets deletes them instead and takes neither. Yes confirms an
// unlink from the sprint's only board, which deletes the sprint.
type UnlinkOptions struct {
	TicketsTo     string
	TargetSprint  string
	DeleteTickets bool
	Yes           bool
}

// check refuses a combination the API would refuse, before any call.
func (o UnlinkOptions) check() error {
	hasTarget := strings.TrimSpace(o.TargetSprint) != ""
	if o.DeleteTickets {
		if strings.TrimSpace(o.TicketsTo) != "" || hasTarget {
			return errors.New("--delete-tickets cannot be combined with --tickets-to or --target-sprint")
		}
		return nil
	}
	switch strings.ToLower(strings.TrimSpace(o.TicketsTo)) {
	case "", ticketsToBacklog:
		if hasTarget {
			return errors.New("--target-sprint is only valid with --tickets-to sprint")
		}
	case ticketsToSprint:
		if !hasTarget {
			return errors.New("--tickets-to sprint needs --target-sprint")
		}
	default:
		return fmt.Errorf("invalid --tickets-to %q: use backlog or sprint, or pass --delete-tickets", o.TicketsTo)
	}
	return nil
}

// Unlink unlinks the sprint that name names from board boardID and returns it. The API deletes a
// sprint that no other board links, so an unlink from its only board needs Yes.
func Unlink(c *client.Client, boardID, name string, opts UnlinkOptions) (client.Sprint, error) {
	if err := opts.check(); err != nil {
		return client.Sprint{}, err
	}
	found, err := Find(c, boardID, name)
	if err != nil {
		return client.Sprint{}, err
	}
	req := client.SprintUnlinkRequest{}
	if opts.DeleteTickets {
		deleteTickets := true
		req.DeleteTickets = &deleteTickets
	} else {
		transfer := client.SprintUnlinkTransfer{BoardID: boardID}
		if strings.TrimSpace(opts.TargetSprint) != "" {
			target, err := Find(c, boardID, opts.TargetSprint)
			if err != nil {
				return client.Sprint{}, fmt.Errorf("failed to resolve target sprint: %w", err)
			}
			if strings.EqualFold(target.ID, found.ID) {
				return client.Sprint{}, errors.New("--target-sprint must be another sprint than the one being unlinked")
			}
			transfer.SprintID = &target.ID
		}
		req.Transfer = &transfer
	}
	detail, err := c.Unrecorded().GetSprint(boardID, found.ID)
	if err != nil {
		return client.Sprint{}, err
	}
	if !opts.Yes && !linkedElsewhere(detail, boardID) {
		return client.Sprint{}, fmt.Errorf("this board is the only board of sprint %s, so unlinking deletes the sprint; pass --yes to confirm", Label(found))
	}
	return found, c.UnlinkSprint(boardID, found.ID, req)
}

// linkedElsewhere reports whether a board other than boardID links the sprint.
func linkedElsewhere(s client.Sprint, boardID string) bool {
	for _, b := range s.Boards {
		if !strings.EqualFold(b.ID, boardID) {
			return true
		}
	}
	return false
}
