package ticket

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Moved is the outcome of a move: where the tickets went, how many and, for a move to another
// board, the tickets whose milestone the move cleared.
type Moved struct {
	Where            string
	Count            int
	MilestoneCleared []string
}

// Move moves the ticket ref names to dest. The move of one ticket cannot change its board, so a
// move to another board is a bulk move of that one ticket.
func Move(c *client.Client, r *Resolver, ref string, dest Destination) (Moved, error) {
	target, err := r.Ticket(ref)
	if err != nil {
		return Moved{}, err
	}
	subIDs, err := dest.subTicketIDs(r)
	if err != nil {
		return Moved{}, err
	}
	if dest.TargetBoard != "" {
		boardID, err := r.Board(dest.TargetBoard)
		if err != nil {
			return Moved{}, err
		}
		moved, err := bulkMove(c, r, []string{target.ID}, boardID, true, dest, subIDs)
		if err != nil {
			return Moved{}, fmt.Errorf("failed to move ticket: %w", err)
		}
		return moved, nil
	}

	req := client.TicketMoveRequest{SubTicketMode: dest.subTickets.mode, SubTicketIDs: subIDs}
	where := "Backlog"
	if dest.Sprint != "" {
		found, err := sprint.Find(r.Client(), target.BoardID, dest.Sprint)
		if err != nil {
			return Moved{}, fmt.Errorf("failed to resolve sprint: %w", err)
		}
		req.SprintID = &found.ID
		where = fmt.Sprintf("Sprint: %s (%s)", cell(found.Name), found.Status)
	} else {
		backlog, err := r.Client().GetBacklog(target.BoardID)
		if err != nil {
			return Moved{}, fmt.Errorf("failed to resolve backlog: %w", err)
		}
		req.BacklogID = &backlog.ID
	}
	if err := c.MoveTicket(target.BoardID, target.ID, req); err != nil {
		return Moved{}, fmt.Errorf("failed to move ticket: %w", err)
	}
	return Moved{Where: where, Count: 1}, nil
}

// bulkMove moves tickets to dest on board boardID, the destination. withTargetBoard also names it
// as targetBoardId, so that tickets of other boards move onto it. The sprint is looked up on the
// destination board, since the API takes only a sprint of that board.
func bulkMove(c *client.Client, r *Resolver, ticketIDs []string, boardID string, withTargetBoard bool, dest Destination, subIDs []string) (Moved, error) {
	req := client.TicketBulkMoveRequest{
		TicketIDs:     ticketIDs,
		Action:        client.RelocationMoveToBacklog,
		SubTicketMode: dest.subTickets.mode,
		SubTicketIDs:  subIDs,
	}
	where := "Backlog"
	if dest.Sprint != "" {
		found, err := sprint.Find(r.Client(), boardID, dest.Sprint)
		if err != nil {
			return Moved{}, fmt.Errorf("failed to resolve sprint: %w", err)
		}
		req.Action = client.RelocationMoveToSprint
		req.TargetSprintID = &found.ID
		where = fmt.Sprintf("Sprint: %s (%s)", cell(found.Name), found.Status)
	}
	if withTargetBoard {
		req.TargetBoardID = &boardID
		where = fmt.Sprintf("%s on board %s", where, dest.TargetBoard)
	}
	result, err := c.BulkMoveTickets(boardID, req)
	if err != nil {
		return Moved{}, err
	}
	return Moved{Where: where, Count: len(ticketIDs), MilestoneCleared: result.MilestoneClearedTicketKeys}, nil
}
