package client

import (
	"fmt"
	"net/url"
)

// Milestone is a dated target on a board. The list returns the fields up to DependsOn; the detail
// adds Description, CreatedAt and UpdatedAt. StartDate and TargetDate are YYYY-MM-DD days.
type Milestone struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Status      string  `json:"status"`
	StartDate   *string `json:"startDate"`
	TargetDate  string  `json:"targetDate"`
	BoardID     string  `json:"boardId"`
	BoardName   string  `json:"boardName"`
	BoardPrefix string  `json:"boardPrefix"`
	DoneCount   int64   `json:"doneCount"`
	TotalCount  int64   `json:"totalCount"`
	Health      string  `json:"health"`
	Color       *string `json:"color"`
	// DependsOn lists the live milestones this one depends on, on boards the caller is a member
	// of, sorted by target date.
	DependsOn   []MilestoneDependency `json:"dependsOn"`
	Description *string               `json:"description,omitempty"`
	CreatedAt   string                `json:"createdAt,omitempty"`
	UpdatedAt   string                `json:"updatedAt,omitempty"`
}

// MilestoneDependency is a milestone another milestone depends on. Conflict is true when the
// dependent starts (or, without a start date, is due) before this one's target date; the API
// never shifts dates.
type MilestoneDependency struct {
	MilestoneID string `json:"milestoneId"`
	Name        string `json:"name"`
	BoardID     string `json:"boardId"`
	TargetDate  string `json:"targetDate"`
	Conflict    bool   `json:"conflict"`
}

// MilestoneCreateRequest is the body of POST /kazan/boards/{boardId}/milestones. Color is
// #RRGGBB; the API takes the board's color when it is nil.
type MilestoneCreateRequest struct {
	Name        string  `json:"name"`
	Description *string `json:"description,omitempty"`
	StartDate   *string `json:"startDate,omitempty"`
	TargetDate  string  `json:"targetDate"`
	Color       *string `json:"color,omitempty"`
}

// MilestoneUpdateRequest is the body of PUT /kazan/boards/{boardId}/milestones/{id}. A nil field
// is left unchanged; the clear flags set their field to null.
type MilestoneUpdateRequest struct {
	Name             *string `json:"name,omitempty"`
	Description      *string `json:"description,omitempty"`
	StartDate        *string `json:"startDate,omitempty"`
	TargetDate       *string `json:"targetDate,omitempty"`
	ClearStartDate   *bool   `json:"clearStartDate,omitempty"`
	ClearDescription *bool   `json:"clearDescription,omitempty"`
	Color            *string `json:"color,omitempty"`
}

// MilestoneTicketsRequest is the body of the milestone link and unlink calls.
type MilestoneTicketsRequest struct {
	TicketIDs []string `json:"ticketIds"`
}

// The body-less state changes of a milestone, as path suffixes.
const (
	MilestoneClose   = "/close"
	MilestoneReopen  = "/reopen"
	MilestoneRestore = "/restore"
)

func milestonesPath(boardID string) string {
	return boardPath(boardID) + "/milestones"
}

func milestonePath(boardID, id string) string {
	return milestonesPath(boardID) + "/" + url.PathEscape(id)
}

// ListMilestones reads the milestones of board boardID; query may hold status and
// includeRelatedBoards.
func (c *Client) ListMilestones(boardID string, query url.Values) ([]Milestone, error) {
	body, err := c.Get(withQuery(milestonesPath(boardID), query))
	if err != nil {
		return nil, err
	}
	return parseData[[]Milestone](body, fmt.Sprintf("the milestones of board %s", boardID))
}

// GetMilestone reads milestone id of its own board boardID.
func (c *Client) GetMilestone(boardID, id string) (Milestone, error) {
	body, err := c.Get(milestonePath(boardID, id))
	if err != nil {
		return Milestone{}, err
	}
	return parseData[Milestone](body, "milestone "+id)
}

// CreateMilestone creates a milestone on board boardID and returns it.
func (c *Client) CreateMilestone(boardID string, req MilestoneCreateRequest) (Milestone, error) {
	body, err := c.Post(milestonesPath(boardID), req)
	if err != nil {
		return Milestone{}, err
	}
	return parseData[Milestone](body, "the created milestone")
}

// UpdateMilestone changes the given fields of milestone id of board boardID and returns it.
func (c *Client) UpdateMilestone(boardID, id string, req MilestoneUpdateRequest) (Milestone, error) {
	body, err := c.Put(milestonePath(boardID, id), req)
	if err != nil {
		return Milestone{}, err
	}
	return parseData[Milestone](body, "the updated milestone")
}

// ChangeMilestoneState posts one of MilestoneClose, MilestoneReopen or MilestoneRestore for
// milestone id of board boardID.
func (c *Client) ChangeMilestoneState(boardID, id, change string) error {
	_, err := c.Post(milestonePath(boardID, id)+change, nil)
	return err
}

// DeleteMilestone deletes milestone id of board boardID and detaches its tickets.
func (c *Client) DeleteMilestone(boardID, id string) error {
	_, err := c.Delete(milestonePath(boardID, id))
	return err
}

// LinkMilestoneTickets attaches tickets to milestone id of board boardID and returns the
// milestone with its new progress.
func (c *Client) LinkMilestoneTickets(boardID, id string, ticketIDs []string) (Milestone, error) {
	return c.milestoneTickets(boardID, id, "/link", ticketIDs)
}

// UnlinkMilestoneTickets detaches tickets from milestone id of board boardID and returns the
// milestone with its new progress.
func (c *Client) UnlinkMilestoneTickets(boardID, id string, ticketIDs []string) (Milestone, error) {
	return c.milestoneTickets(boardID, id, "/unlink", ticketIDs)
}

func (c *Client) milestoneTickets(boardID, id, suffix string, ticketIDs []string) (Milestone, error) {
	body, err := c.Post(milestonePath(boardID, id)+suffix, MilestoneTicketsRequest{TicketIDs: ticketIDs})
	if err != nil {
		return Milestone{}, err
	}
	return parseData[Milestone](body, "milestone "+id)
}

// AddMilestoneDependency records that milestone id of board boardID depends on milestone dependsOn
// and returns the dependent with its dependencies.
func (c *Client) AddMilestoneDependency(boardID, id, dependsOn string) (Milestone, error) {
	body, err := c.Post(milestoneDependencyPath(boardID, id, dependsOn), nil)
	if err != nil {
		return Milestone{}, err
	}
	return parseData[Milestone](body, "milestone "+id)
}

// RemoveMilestoneDependency removes the dependency of milestone id of board boardID on dependsOn
// and returns the dependent with its remaining dependencies.
func (c *Client) RemoveMilestoneDependency(boardID, id, dependsOn string) (Milestone, error) {
	body, err := c.Delete(milestoneDependencyPath(boardID, id, dependsOn))
	if err != nil {
		return Milestone{}, err
	}
	return parseData[Milestone](body, "milestone "+id)
}

func milestoneDependencyPath(boardID, id, dependsOn string) string {
	return milestonePath(boardID, id) + "/dependencies/" + url.PathEscape(dependsOn)
}
