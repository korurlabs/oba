package ticket

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ErrNoTickets is the error of a bulk command without --tickets.
var ErrNoTickets = errors.New("--tickets is required: comma-separated ticket keys or ids")

// BulkMove moves the tickets refs names to dest. The destination board is --target-board when
// given, else the board all tickets share; tickets of several boards without a target have no
// single destination and are refused.
func BulkMove(c *client.Client, r *Resolver, refs []string, dest Destination) (Moved, error) {
	if len(refs) == 0 {
		return Moved{}, ErrNoTickets
	}
	ticketIDs := make([]string, 0, len(refs))
	boardIDs := make([]string, 0, len(refs))
	for _, ref := range refs {
		target, err := r.Ticket(ref)
		if err != nil {
			return Moved{}, fmt.Errorf("failed to resolve ticket %s: %w", ref, err)
		}
		ticketIDs = append(ticketIDs, target.ID)
		boardIDs = append(boardIDs, target.BoardID)
	}
	subIDs, err := dest.subTicketIDs(r)
	if err != nil {
		return Moved{}, err
	}
	withTargetBoard := dest.TargetBoard != ""
	var boardID string
	if withTargetBoard {
		boardID, err = r.Board(dest.TargetBoard)
	} else {
		boardID, err = commonBoard(refs, boardIDs)
	}
	if err != nil {
		return Moved{}, err
	}
	moved, err := bulkMove(c, r, ticketIDs, boardID, withTargetBoard, dest, subIDs)
	if err != nil {
		return Moved{}, fmt.Errorf("failed to bulk move tickets: %w", err)
	}
	return moved, nil
}

// commonBoard returns the board every ticket is on, or an error naming the tickets of each board
// when they differ.
func commonBoard(refs, boardIDs []string) (string, error) {
	byBoard := map[string][]string{}
	var order []string
	for i, boardID := range boardIDs {
		key := strings.ToLower(boardID)
		if _, seen := byBoard[key]; !seen {
			order = append(order, key)
		}
		byBoard[key] = append(byBoard[key], refs[i])
	}
	if len(order) == 1 {
		return boardIDs[0], nil
	}
	groups := make([]string, len(order))
	for i, key := range order {
		groups[i] = strings.Join(byBoard[key], ",")
	}
	return "", fmt.Errorf("the tickets are on %d different boards (%s); pass --target-board to move them all to one board",
		len(order), strings.Join(groups, " | "))
}
