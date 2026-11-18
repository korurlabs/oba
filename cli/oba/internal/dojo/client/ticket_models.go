package client

// TicketPerson is a person on a ticket: an assignee, a reviewer or its creator.
type TicketPerson struct {
	ID        string `json:"id"`
	UserID    string `json:"userId"`
	Email     string `json:"email"`
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
}

// TicketSystemActor is the system actor, such as Alertmanager, that created a ticket without a
// user behind it.
type TicketSystemActor struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Type string `json:"type"`
}

// TicketNamed is the id and name the API sends for the project, sprint and backlog of a ticket.
type TicketNamed struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// TicketBoard is the board a ticket lives on.
type TicketBoard struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Prefix string `json:"prefix"`
}

// TicketMilestone is the milestone a ticket belongs to.
type TicketMilestone struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	Status     string `json:"status"`
	TargetDate string `json:"targetDate"`
	BoardID    string `json:"boardId"`
}

// Ticket is one ticket as the ticket lists and the lookup by key return it.
type Ticket struct {
	ID                   string             `json:"id"`
	Key                  string             `json:"key"`
	Title                string             `json:"title"`
	Status               string             `json:"status"`
	Priority             string             `json:"priority"`
	Type                 string             `json:"type"`
	Weight               *int               `json:"weight"`
	DueDate              *string            `json:"dueDate"`
	CreatedAt            string             `json:"createdAt"`
	CreatedBy            *TicketPerson      `json:"createdBy"`
	CreatedBySystemActor *TicketSystemActor `json:"createdBySystemActor"`
	Assignees            []TicketPerson     `json:"assignees"`
	Reviewers            []TicketPerson     `json:"reviewers"`
	Project              *TicketNamed       `json:"project"`
	Labels               []Label            `json:"labels"`
	Board                *TicketBoard       `json:"board"`
	Sprint               *TicketNamed       `json:"sprint"`
	Backlog              *TicketNamed       `json:"backlog"`
	Milestone            *TicketMilestone   `json:"milestone"`
	// Percentage is the ticket's progress, 0 to 100.
	Percentage int `json:"percentage"`
}

// TicketDetail is one ticket as GET /kazan/boards/{boardId}/tickets/{id} returns it.
type TicketDetail struct {
	Ticket
	Description    *string `json:"description"`
	RCA            *string `json:"rca"`
	LessonsLearned *string `json:"lessonsLearned"`
	MissionRank    *string `json:"missionRank"`
}

// TicketCreateRequest is the body of POST /kazan/boards/{boardId}/tickets. MissionRank is
// always sent: the API refuses a ticket without its size estimate. DueDate is an RFC 3339
// instant.
type TicketCreateRequest struct {
	Title          string   `json:"title"`
	Type           string   `json:"type"`
	Priority       string   `json:"priority"`
	Status         string   `json:"status"`
	Description    *string  `json:"description,omitempty"`
	SprintID       *string  `json:"sprintId,omitempty"`
	BacklogID      *string  `json:"backlogId,omitempty"`
	ProjectID      *string  `json:"projectId,omitempty"`
	MilestoneID    *string  `json:"milestoneId,omitempty"`
	AssigneeIDs    []string `json:"assigneeIds,omitempty"`
	ReviewerIDs    []string `json:"reviewerIds,omitempty"`
	LabelIDs       []string `json:"labelIds,omitempty"`
	ParentTicketID *string  `json:"parentTicketId,omitempty"`
	Weight         *int     `json:"weight,omitempty"`
	MissionRank    string   `json:"missionRank"`
	RCA            *string  `json:"rca,omitempty"`
	LessonsLearned *string  `json:"lessonsLearned,omitempty"`
	DueDate        *string  `json:"dueDate,omitempty"`
}

// TicketUpdateRequest is the body of PUT /kazan/boards/{boardId}/tickets/{id}; a nil field is
// left unchanged. It carries no sprint or backlog: placement changes go through the moves.
type TicketUpdateRequest struct {
	Title          *string  `json:"title,omitempty"`
	Description    *string  `json:"description,omitempty"`
	Type           *string  `json:"type,omitempty"`
	Status         *string  `json:"status,omitempty"`
	Priority       *string  `json:"priority,omitempty"`
	ProjectID      *string  `json:"projectId,omitempty"`
	MilestoneID    *string  `json:"milestoneId,omitempty"`
	ClearMilestone *bool    `json:"clearMilestone,omitempty"`
	ParentTicketID *string  `json:"parentTicketId,omitempty"`
	ClearParent    *bool    `json:"clearParent,omitempty"`
	Percentage     *int     `json:"percentage,omitempty"`
	Weight         *int     `json:"weight,omitempty"`
	MissionRank    *string  `json:"missionRank,omitempty"`
	RCA            *string  `json:"rca,omitempty"`
	LessonsLearned *string  `json:"lessonsLearned,omitempty"`
	DueDate        *string  `json:"dueDate,omitempty"`
	AssigneeIDs    []string `json:"assigneeIds,omitempty"`
	ReviewerIDs    []string `json:"reviewerIds,omitempty"`
	LabelIDs       []string `json:"labelIds,omitempty"`
}

// TicketMoveRequest is the body of a move of one ticket within its own board: exactly one of
// SprintID and BacklogID is set. A move to another board is a bulk move.
type TicketMoveRequest struct {
	SprintID      *string  `json:"sprintId,omitempty"`
	BacklogID     *string  `json:"backlogId,omitempty"`
	SubTicketMode string   `json:"subTicketMode,omitempty"`
	SubTicketIDs  []string `json:"subTicketIds,omitempty"`
}

// The SubTicketMoveMode values. A move never detaches a sub-ticket: the mode only picks which
// sub-tickets go along. UNFINISHED, the API's default, takes every sub-ticket at any depth that
// is not DONE and sits where its parent was; NONE takes none; SELECTED takes exactly
// SubTicketIDs.
const (
	SubTicketModeNone       = "NONE"
	SubTicketModeUnfinished = "UNFINISHED"
	SubTicketModeSelected   = "SELECTED"
)

// The TicketRelocationAction values a bulk move takes.
const (
	RelocationMoveToBacklog = "MOVE_TO_BACKLOG"
	RelocationMoveToSprint  = "MOVE_TO_SPRINT"
)

// TicketBulkMoveRequest is the body of POST /kazan/boards/{boardId}/tickets/bulk-move. The
// backlog is always the one of the board in the path, so the request names none.
type TicketBulkMoveRequest struct {
	TicketIDs      []string `json:"ticketIds"`
	Action         string   `json:"action"`
	TargetSprintID *string  `json:"targetSprintId,omitempty"`
	TargetBoardID  *string  `json:"targetBoardId,omitempty"`
	SubTicketMode  string   `json:"subTicketMode,omitempty"`
	SubTicketIDs   []string `json:"subTicketIds,omitempty"`
}

// TicketBulkMoveResult is the result of a bulk move. MilestoneClearedTicketKeys lists the tickets
// that lost their milestone because the target board is not related to the milestone's board.
type TicketBulkMoveResult struct {
	MovedCount                 int      `json:"movedCount"`
	MilestoneClearedTicketKeys []string `json:"milestoneClearedTicketKeys"`
}

// ticketOrderRequest is the body of PUT .../tickets/{id}/order. The API takes the sprint or
// backlog from the ticket itself.
type ticketOrderRequest struct {
	Order int `json:"order"`
}
