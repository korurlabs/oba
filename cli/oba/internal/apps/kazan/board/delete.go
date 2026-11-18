package board

import (
	"errors"
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// The values of the API's TicketRelocationAction and BoardChildBoardAction that board delete
// sends.
const (
	ticketsToBacklog = "MOVE_TO_BACKLOG"
	ticketsToSprint  = "MOVE_TO_SPRINT"
	ticketsDelete    = "DELETE"

	childrenPromote = "PROMOTE"
	childrenMove    = "MOVE"
	childrenDelete  = "DELETE"
)

// DeleteOptions says where the tickets and child boards of a deleted board go. TicketsTo is
// backlog, sprint or delete; TargetBoard and TargetSprint receive moved tickets. Children is
// promote, move or delete; NewParent receives moved children. Yes confirms a delete of tickets
// or child boards. The API asks for a ticket action only when the board has tickets and a child
// action only when it has children, so nothing is sent unless given.
type DeleteOptions struct {
	TicketsTo    string
	TargetBoard  string
	TargetSprint string
	Children     string
	NewParent    string
	Yes          bool
}

// Delete deletes the board ref names. The options are checked before any call.
func Delete(c *client.Client, ref string, opts DeleteOptions) (Ref, error) {
	ticketAction, childAction, err := checkDelete(opts)
	if err != nil {
		return Ref{}, err
	}
	resolver := NewResolver(c)
	named, err := resolver.Resolve(ref)
	if err != nil {
		return Ref{}, err
	}
	query := url.Values{}
	if ticketAction != "" {
		query.Set("ticketAction", ticketAction)
	}
	if ticketAction == ticketsToBacklog || ticketAction == ticketsToSprint {
		target, err := resolver.Resolve(opts.TargetBoard)
		if err != nil {
			return Ref{}, err
		}
		if strings.EqualFold(target.ID, named.ID) {
			return Ref{}, errors.New("--target-board must be another board than the one being deleted")
		}
		query.Set("targetBoardId", target.ID)
		if ticketAction == ticketsToSprint {
			s, err := sprint.Find(c, target.ID, opts.TargetSprint)
			if err != nil {
				return Ref{}, err
			}
			query.Set("targetSprintId", s.ID)
		}
	}
	if childAction != "" {
		query.Set("childBoardAction", childAction)
	}
	if childAction == childrenMove {
		parent, err := resolver.Resolve(opts.NewParent)
		if err != nil {
			return Ref{}, err
		}
		query.Set("newParentBoardId", parent.ID)
	}
	return named, c.DeleteBoard(named.ID, query)
}

// checkDelete maps the options to the API's actions and refuses a combination the API would
// refuse or that deletes without --yes.
func checkDelete(opts DeleteOptions) (ticketAction, childAction string, err error) {
	ticketsTo := strings.ToLower(strings.TrimSpace(opts.TicketsTo))
	hasTarget := strings.TrimSpace(opts.TargetBoard) != "" || strings.TrimSpace(opts.TargetSprint) != ""
	switch ticketsTo {
	case "":
		if hasTarget {
			return "", "", errors.New("--target-board and --target-sprint need --tickets-to backlog or sprint")
		}
	case "delete":
		if hasTarget {
			return "", "", errors.New("--target-board and --target-sprint are not valid with --tickets-to delete")
		}
		if !opts.Yes {
			return "", "", errors.New("--tickets-to delete deletes every ticket of the board; pass --yes to confirm")
		}
		ticketAction = ticketsDelete
	case "backlog", "sprint":
		if strings.TrimSpace(opts.TargetBoard) == "" {
			return "", "", fmt.Errorf("--tickets-to %s needs --target-board: the deleted board's backlog and sprints go with it", ticketsTo)
		}
		hasSprint := strings.TrimSpace(opts.TargetSprint) != ""
		if ticketsTo == "sprint" && !hasSprint {
			return "", "", errors.New("--tickets-to sprint needs --target-sprint")
		}
		if ticketsTo == "backlog" && hasSprint {
			return "", "", errors.New("--target-sprint is only valid with --tickets-to sprint")
		}
		ticketAction = ticketsToBacklog
		if ticketsTo == "sprint" {
			ticketAction = ticketsToSprint
		}
	default:
		return "", "", fmt.Errorf("invalid --tickets-to %q: use backlog, sprint or delete", opts.TicketsTo)
	}

	children := strings.ToLower(strings.TrimSpace(opts.Children))
	hasNewParent := strings.TrimSpace(opts.NewParent) != ""
	if hasNewParent && children != "move" {
		return "", "", errors.New("--new-parent is only valid with --children move")
	}
	switch children {
	case "":
	case "promote":
		childAction = childrenPromote
	case "move":
		if !hasNewParent {
			return "", "", errors.New("--children move needs --new-parent")
		}
		childAction = childrenMove
	case "delete":
		if !opts.Yes {
			return "", "", errors.New("--children delete deletes every child board; pass --yes to confirm")
		}
		childAction = childrenDelete
	default:
		return "", "", fmt.Errorf("invalid --children %q: use promote, move or delete", opts.Children)
	}
	return ticketAction, childAction, nil
}
