package ticket

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// CreateInput is the flags of ticket create as typed; an empty text is not sent.
type CreateInput struct {
	Title          string
	Type           string
	Priority       string
	Status         string
	MissionRank    string
	Description    string
	RCA            string
	LessonsLearned string
	Assignee       string
	Reviewer       string
	Label          string
	Project        string
	Milestone      string
	Sprint         string
	Backlog        string
	Parent         string
	DueDate        string
	StoryPoints    *int
	Attach         []string
}

// ErrParentPlacement refuses --sprint or --backlog on a sub-ticket: the API places a sub-ticket in
// its parent's sprint or backlog and refuses a request that names another one.
var ErrParentPlacement = errors.New("--sprint and --backlog cannot be used with --parent: a sub-ticket goes to its parent's sprint or backlog")

// PreparedCreate is a create whose flags and files were checked before any call.
type PreparedCreate struct {
	in    CreateInput
	req   client.TicketCreateRequest
	files []client.AttachmentFile
	// RetiredType is set when --type TASK was taken as FEATURE.
	RetiredType bool
}

// Created is a new ticket: as read, as the API sent it, and the outcome of its attachments.
// UploadErr is set when the ticket exists but its files could not be attached.
type Created struct {
	Ticket      client.Ticket
	Raw         json.RawMessage
	Attachments []client.Attachment
	UploadErr   error
	Files       []string
}

// PrepareCreate checks the flags and reads the --attach files, so that a bad value or file fails
// before anything is sent.
func PrepareCreate(in CreateInput) (PreparedCreate, error) {
	files, err := attachment.ReadFiles(in.Attach)
	if err != nil {
		return PreparedCreate{}, err
	}
	p := PreparedCreate{files: files}
	in.Parent = strings.TrimSpace(in.Parent)
	if in.Parent != "" && (in.Sprint != "" || in.Backlog != "") {
		return PreparedCreate{}, ErrParentPlacement
	}
	required := []struct{ flag, value string }{
		{"title", in.Title}, {"type", in.Type}, {"priority", in.Priority}, {"status", in.Status}, {"mission-rank", in.MissionRank},
	}
	for _, r := range required {
		if strings.TrimSpace(r.value) == "" {
			return PreparedCreate{}, fmt.Errorf("--%s is required", r.flag)
		}
	}
	p.req.Title = in.Title
	if p.req.Type, p.RetiredType, err = TypeValue(in.Type); err != nil {
		return PreparedCreate{}, err
	}
	if p.req.Priority, err = EnumValue("priority", in.Priority, Priorities); err != nil {
		return PreparedCreate{}, err
	}
	if p.req.Status, err = EnumValue("status", in.Status, Statuses); err != nil {
		return PreparedCreate{}, err
	}
	if p.req.MissionRank, err = EnumValue("mission-rank", in.MissionRank, MissionRanks); err != nil {
		return PreparedCreate{}, err
	}
	if in.DueDate != "" {
		due, err := dates.ToAPIInstant(in.DueDate)
		if err != nil {
			return PreparedCreate{}, err
		}
		p.req.DueDate = &due
	}
	p.req.Description = given(in.Description)
	p.req.RCA = given(in.RCA)
	p.req.LessonsLearned = given(in.LessonsLearned)
	p.req.ProjectID = given(in.Project)
	p.req.LabelIDs = userinput.SplitCSV(in.Label)
	p.req.Weight = in.StoryPoints
	p.in = in
	return p, nil
}

// Create creates the prepared ticket on the selected board, or as a sub-ticket on its parent's
// board, then attaches its files. c records the created ticket for --json; r makes the lookups;
// warn receives the warnings of the attachment check.
func Create(c *client.Client, r *Resolver, p PreparedCreate, warn io.Writer) (Created, error) {
	req := p.req
	boardID, err := createBoard(r, p.in.Parent, &req)
	if err != nil {
		return Created{}, err
	}
	if req.AssigneeIDs, err = assigneeIDs(r.Client(), boardID, p.in.Assignee); err != nil {
		return Created{}, err
	}
	if req.ReviewerIDs, err = peopleIDs(r.Client(), "reviewer", p.in.Reviewer); err != nil {
		return Created{}, err
	}
	if p.in.Milestone != "" {
		m, err := milestone.Resolve(r.Client(), boardID, p.in.Milestone)
		if err != nil {
			return Created{}, err
		}
		req.MilestoneID = &m.ID
	}
	// A sub-ticket takes its parent's sprint or backlog, and the API refuses a request that also
	// names one, so its placement is left out.
	if req.ParentTicketID == nil {
		if err := place(r, boardID, p.in.Sprint, p.in.Backlog, &req); err != nil {
			return Created{}, err
		}
	}
	// Last check before the step that cannot be undone.
	if err := CheckAttachmentDuplicates(r.Client(), p.files, warn); err != nil {
		return Created{}, err
	}
	created, raw, err := c.CreateTicket(boardID, req)
	if err != nil {
		return Created{}, fmt.Errorf("failed to create ticket: %w", explainAgentReviewer(err))
	}
	result := Created{Ticket: created, Raw: raw}
	if len(p.files) == 0 {
		return result, nil
	}
	for _, f := range p.files {
		result.Files = append(result.Files, f.FileName)
	}
	result.Attachments, result.UploadErr = attachment.Add(r.Client(), AttachmentEntityType, created.ID, p.files)
	return result, nil
}

// createBoard returns the board of the new ticket. A sub-ticket must be on its parent's board,
// so a parent given by key picks the board; a parent id carries no board and uses the selected
// board.
func createBoard(r *Resolver, parent string, req *client.TicketCreateRequest) (string, error) {
	if parent == "" {
		return r.SelectedBoard()
	}
	target, err := r.Ticket(parent)
	if err != nil {
		return "", fmt.Errorf("failed to resolve --parent: %w", err)
	}
	req.ParentTicketID = &target.ID
	return target.BoardID, nil
}

// place puts the new ticket in the sprint sprintName names, in the backlog backlogID, or, with
// neither, in the board's backlog.
func place(r *Resolver, boardID, sprintName, backlogID string, req *client.TicketCreateRequest) error {
	if sprintName != "" {
		found, err := sprint.Find(r.Client(), boardID, sprintName)
		if err != nil {
			return fmt.Errorf("failed to resolve sprint: %w", err)
		}
		req.SprintID = &found.ID
	}
	if backlogID != "" {
		req.BacklogID = &backlogID
	}
	if req.SprintID != nil || req.BacklogID != nil {
		return nil
	}
	backlog, err := r.Client().GetBacklog(boardID)
	if err != nil {
		return fmt.Errorf("failed to find the backlog of the board: %w; use --backlog or --sprint", err)
	}
	if backlog.ID != "" {
		req.BacklogID = &backlog.ID
	}
	return nil
}

// peopleIDs resolves a comma-separated list of emails or user ids; an empty list is nil.
func peopleIDs(c *client.Client, flag, csv string) ([]string, error) {
	refs := userinput.SplitCSV(csv)
	if len(refs) == 0 {
		return nil, nil
	}
	ids, err := organization.ResolveMemberIDs(c, refs)
	if err != nil {
		return nil, fmt.Errorf("--%s: %w", flag, err)
	}
	return ids, nil
}

// given is a pointer to value, or nil when it is empty.
func given(value string) *string {
	if value == "" {
		return nil
	}
	return &value
}
