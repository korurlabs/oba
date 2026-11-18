package ticket

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ListInput is what ticket list reads on one board: the filters, the sprint named by the
// arguments ("sprint" for the running one, empty for the backlog), a project, a milestone and
// whether the related parent and child boards count too.
type ListInput struct {
	Filters
	Sprint             string
	Project            string
	Milestone          string
	IncludeChildBoards bool
}

// Listed is the result of a list: a heading that says where the tickets are, and the tickets.
type Listed struct {
	Header  string
	Tickets []client.Ticket
}

// List reads the tickets of the selected board: those of the sprint in.Sprint names, else those
// of the milestone in.Milestone names wherever they sit on the board, sub-tickets included, else
// the backlog's. c records the list for --json; r makes the lookups.
func List(c *client.Client, r *Resolver, in ListInput) (Listed, error) {
	q, err := in.query(r.Client())
	if err != nil {
		return Listed{}, err
	}
	if v := strings.TrimSpace(in.Project); v != "" {
		q.Set("projectId", v)
	}
	if in.IncludeChildBoards {
		q.Set("includeAllBoards", "true")
	}
	boardID, err := r.SelectedBoard()
	if err != nil {
		return Listed{}, err
	}

	milestoneHeader := ""
	if in.Milestone != "" {
		m, err := milestone.Resolve(r.Client(), boardID, in.Milestone)
		if err != nil {
			return Listed{}, err
		}
		q.Set("milestoneId", m.ID)
		milestoneHeader = "Milestone: " + milestone.Label(m)
	}

	sprintName := strings.TrimSpace(in.Sprint)
	switch {
	case sprintName == "" && milestoneHeader != "":
		// The board list needs a sprint or a backlog, so a milestone alone goes to the list of
		// every board, narrowed to this one, where the milestone spans sprints and backlog.
		q.Set("boardIds", boardID)
		tickets, err := c.ListTickets(q)
		return Listed{Header: milestoneHeader, Tickets: tickets}, listFailed(err)
	case sprintName == "":
		backlog, err := r.Client().GetBacklog(boardID)
		if err != nil {
			return Listed{}, fmt.Errorf("failed to resolve backlog: %w", err)
		}
		q.Set("backlogId", backlog.ID)
		tickets, err := c.ListBoardTickets(boardID, q)
		return Listed{Header: "Backlog", Tickets: tickets}, listFailed(err)
	default:
		found, err := sprint.Find(r.Client(), boardID, sprintName)
		if err != nil {
			return Listed{}, fmt.Errorf("failed to resolve sprint: %w", err)
		}
		q.Set("sprintId", found.ID)
		header := fmt.Sprintf("Sprint: %s (%s)", cell(found.Name), found.Status)
		if milestoneHeader != "" {
			header += ", " + milestoneHeader
		}
		tickets, err := c.ListBoardTickets(boardID, q)
		return Listed{Header: header, Tickets: tickets}, listFailed(err)
	}
}

// AcrossInput is what ticket all and mine read: the filters, the boards to keep (names, key
// prefixes or ids, comma separated) and, for all, a milestone.
type AcrossInput struct {
	Filters
	Boards    string
	Milestone string
}

// ListAll reads the tickets of every board the user is a member of, or of the boards in.Boards
// names. A milestone id is sent as given; a name is resolved on the selected board.
func ListAll(c *client.Client, r *Resolver, in AcrossInput) ([]client.Ticket, error) {
	q, err := in.query(r.Client())
	if err != nil {
		return nil, err
	}
	if err := addBoards(r, in.Boards, q.Add); err != nil {
		return nil, err
	}
	if v := strings.TrimSpace(in.Milestone); v != "" {
		id := v
		if !userinput.IsID(v) {
			boardID, err := r.SelectedBoard()
			if err != nil {
				return nil, fmt.Errorf("a milestone name is resolved on a board: %w", err)
			}
			m, err := milestone.Resolve(r.Client(), boardID, v)
			if err != nil {
				return nil, err
			}
			id = m.ID
		}
		q.Set("milestoneId", id)
	}
	tickets, err := c.ListTickets(q)
	return tickets, listFailed(err)
}

// ListMine reads the tickets assigned to user userID, the signed-in user, on every board or on
// the boards in.Boards names.
func ListMine(c *client.Client, r *Resolver, userID string, in AcrossInput) ([]client.Ticket, error) {
	q, err := in.query(r.Client())
	if err != nil {
		return nil, err
	}
	q.Add("assigneeIds", userID)
	if err := addBoards(r, in.Boards, q.Add); err != nil {
		return nil, err
	}
	tickets, err := c.ListTickets(q)
	return tickets, listFailed(err)
}

func addBoards(r *Resolver, boards string, add func(key, value string)) error {
	for _, ref := range userinput.SplitCSV(boards) {
		id, err := r.Board(ref)
		if err != nil {
			return fmt.Errorf("--boards: %w", err)
		}
		add("boardIds", id)
	}
	return nil
}

// listFailed wraps the error of the list call itself, nil staying nil.
func listFailed(err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("failed to list tickets: %w", err)
}
