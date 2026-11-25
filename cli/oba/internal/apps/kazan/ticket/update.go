package ticket

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// UpdateInput is the flags of ticket update; a nil field was not given and is left unchanged.
// An empty Milestone or Parent detaches the ticket from it.
type UpdateInput struct {
	Title          *string
	Description    *string
	Type           *string
	Status         *string
	Priority       *string
	MissionRank    *string
	Project        *string
	Milestone      *string
	Assignee       *string
	Reviewer       *string
	Label          *string
	DueDate        *string
	RCA            *string
	LessonsLearned *string
	Parent         *string
	StoryPoints    *int
	Percentage     *int
}

// ErrUpdatePlacement refuses --sprint or --backlog on ticket update: the update cannot change a
// ticket's sprint or backlog.
var ErrUpdatePlacement = errors.New("ticket update cannot change the sprint or backlog: use 'oba kazan ticket move KEY --sprint NAME' or '--backlog'")

// PreparedUpdate is an update whose flags were checked before any call.
type PreparedUpdate struct {
	in  UpdateInput
	req client.TicketUpdateRequest
	// RetiredType is set when --type TASK was taken as FEATURE.
	RetiredType bool
}

// PrepareUpdate checks the enum and date flags and refuses an update that changes nothing.
func PrepareUpdate(in UpdateInput) (PreparedUpdate, error) {
	p := PreparedUpdate{in: in}
	req := &p.req
	if in.Type != nil {
		typ, retired, err := TypeValue(*in.Type)
		if err != nil {
			return PreparedUpdate{}, err
		}
		req.Type, p.RetiredType = &typ, retired
	}
	enums := []struct {
		flag    string
		value   *string
		allowed []string
		target  **string
	}{
		{"status", in.Status, Statuses, &req.Status},
		{"priority", in.Priority, Priorities, &req.Priority},
		{"mission-rank", in.MissionRank, MissionRanks, &req.MissionRank},
	}
	for _, e := range enums {
		if e.value == nil {
			continue
		}
		v, err := EnumValue(e.flag, *e.value, e.allowed)
		if err != nil {
			return PreparedUpdate{}, err
		}
		*e.target = &v
	}
	if in.DueDate != nil {
		due, err := dates.ToAPIInstant(*in.DueDate)
		if err != nil {
			return PreparedUpdate{}, err
		}
		req.DueDate = &due
	}
	req.Title, req.Description, req.ProjectID = in.Title, in.Description, in.Project
	req.RCA, req.LessonsLearned = in.RCA, in.LessonsLearned
	req.Weight, req.Percentage = in.StoryPoints, in.Percentage
	if in.Label != nil {
		req.LabelIDs = userinput.SplitCSV(*in.Label)
	}
	if !p.changes() {
		return PreparedUpdate{}, terminal.ErrNothingToUpdate
	}
	return p, nil
}

// changes reports whether the update sends anything.
func (p PreparedUpdate) changes() bool {
	in := p.in
	for _, v := range []*string{in.Title, in.Description, in.Type, in.Status, in.Priority, in.MissionRank, in.Project,
		in.Milestone, in.Assignee, in.Reviewer, in.Label, in.DueDate, in.RCA, in.LessonsLearned, in.Parent} {
		if v != nil {
			return true
		}
	}
	return in.StoryPoints != nil || in.Percentage != nil
}

// Update changes the given fields of the ticket ref names. c records the updated ticket for
// --json; r makes the lookups.
func Update(c *client.Client, r *Resolver, ref string, p PreparedUpdate) (client.Ticket, error) {
	target, err := r.Ticket(ref)
	if err != nil {
		return client.Ticket{}, err
	}
	req := p.req
	in := p.in
	if in.Milestone != nil {
		if strings.TrimSpace(*in.Milestone) == "" {
			detach := true
			req.ClearMilestone = &detach
		} else {
			m, err := milestone.Resolve(r.Client(), target.BoardID, *in.Milestone)
			if err != nil {
				return client.Ticket{}, err
			}
			req.MilestoneID = &m.ID
		}
	}
	if in.Assignee != nil {
		if req.AssigneeIDs, err = assigneeIDs(r.Client(), target.BoardID, *in.Assignee); err != nil {
			return client.Ticket{}, err
		}
	}
	if in.Reviewer != nil {
		if req.ReviewerIDs, err = peopleIDs(r.Client(), "reviewer", *in.Reviewer); err != nil {
			return client.Ticket{}, err
		}
	}
	if in.Parent != nil {
		parent := strings.TrimSpace(*in.Parent)
		if parent == "" {
			detach := true
			req.ClearParent = &detach
		} else {
			parentID, err := updateParent(r, parent, target)
			if err != nil {
				return client.Ticket{}, err
			}
			req.ParentTicketID = &parentID
		}
	}
	updated, err := c.UpdateTicket(target.BoardID, target.ID, req)
	if err != nil {
		return client.Ticket{}, fmt.Errorf("failed to update ticket: %w", explainAgentReviewer(err))
	}
	return updated, nil
}

// updateParent resolves the --parent of an update to a ticket id. The API looks the parent up on
// the ticket's own board, so a key of another board is refused here with a clear message rather
// than as "ticket not found" from the API. An id carries no board and is sent as given.
func updateParent(r *Resolver, ref string, child Target) (string, error) {
	parentID := ref
	if !userinput.IsID(ref) {
		parent, err := r.Ticket(ref)
		if err != nil {
			return "", fmt.Errorf("failed to resolve --parent: %w", err)
		}
		if !strings.EqualFold(parent.BoardID, child.BoardID) {
			return "", fmt.Errorf("parent %s is on another board than the ticket; a sub-ticket must be on its parent's board", ref)
		}
		parentID = parent.ID
	}
	if strings.EqualFold(parentID, child.ID) {
		return "", errors.New("a ticket cannot be its own parent")
	}
	return parentID, nil
}
