package ticket

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// DestinationInput is the destination flags of ticket move and bulk-move as typed.
// TargetSprintGiven and TargetBacklogGiven report the retired flags that took ids.
type DestinationInput struct {
	Sprint             string
	Backlog            bool
	TargetBoard        string
	ToBoard            string
	SubTickets         string
	TargetSprintGiven  bool
	TargetBacklogGiven bool
}

// Destination is where a move sends tickets: a sprint by name ("sprint" for the running one, else
// the newest) or the backlog, optionally on another board, and which sub-tickets go along.
type Destination struct {
	Sprint      string
	Backlog     bool
	TargetBoard string
	subTickets  subTicketChoice
}

// subTicketChoice is the --sub-tickets value: a mode and, for SELECTED, the sub-tickets' keys or
// ids.
type subTicketChoice struct {
	mode string
	refs []string
}

// errTargetFlagsRetired refuses the retired --target-sprint and --target-backlog, which took ids.
var errTargetFlagsRetired = errors.New("--target-sprint and --target-backlog were removed: use --sprint NAME (or --sprint sprint for the running sprint) or --backlog")

// ParseDestination checks the destination flags before any call: exactly one of --sprint and
// --backlog, and a --sub-tickets value of none, unfinished or sub-ticket keys or ids.
func ParseDestination(in DestinationInput) (Destination, error) {
	if in.TargetSprintGiven || in.TargetBacklogGiven {
		return Destination{}, errTargetFlagsRetired
	}
	targetBoard := strings.TrimSpace(in.TargetBoard)
	if toBoard := strings.TrimSpace(in.ToBoard); toBoard != "" {
		if targetBoard != "" && !strings.EqualFold(targetBoard, toBoard) {
			return Destination{}, errors.New("--to-board and --target-board name different boards; pass one")
		}
		targetBoard = toBoard
	}
	dest := Destination{Sprint: strings.TrimSpace(in.Sprint), Backlog: in.Backlog, TargetBoard: targetBoard}
	if (dest.Sprint == "") == !dest.Backlog {
		return Destination{}, errors.New("pass exactly one of --sprint NAME or --backlog")
	}
	sub, err := parseSubTickets(in.SubTickets)
	if err != nil {
		return Destination{}, err
	}
	dest.subTickets = sub
	return dest, nil
}

// parseSubTickets reads "unfinished" or "none" in any case, or comma-separated sub-ticket keys or
// ids, which select exactly those sub-tickets.
func parseSubTickets(value string) (subTicketChoice, error) {
	trimmed := strings.TrimSpace(value)
	switch strings.ToLower(trimmed) {
	case "unfinished":
		return subTicketChoice{mode: client.SubTicketModeUnfinished}, nil
	case "none":
		return subTicketChoice{mode: client.SubTicketModeNone}, nil
	}
	refs := userinput.SplitCSV(trimmed)
	if len(refs) == 0 {
		return subTicketChoice{}, errors.New("--sub-tickets needs none, unfinished or comma-separated sub-ticket keys")
	}
	for _, ref := range refs {
		if userinput.IsID(ref) {
			continue
		}
		if _, _, err := ParseKey(ref); err != nil {
			return subTicketChoice{}, fmt.Errorf("--sub-tickets: %q is not none, unfinished, a ticket key or an id", ref)
		}
	}
	return subTicketChoice{mode: client.SubTicketModeSelected, refs: refs}, nil
}

// subTicketIDs turns the selected sub-ticket keys into ids; an id is sent as given. The API
// checks that each one is a moved ticket or a sub-ticket, at any depth, of one.
func (d Destination) subTicketIDs(r *Resolver) ([]string, error) {
	var ids []string
	for _, ref := range d.subTickets.refs {
		if userinput.IsID(ref) {
			ids = append(ids, ref)
			continue
		}
		target, err := r.Ticket(ref)
		if err != nil {
			return nil, fmt.Errorf("failed to resolve sub-ticket %s: %w", ref, err)
		}
		ids = append(ids, target.ID)
	}
	return ids, nil
}
