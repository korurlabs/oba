package client

// DailyUpdateTicketRequest is one ticket line of a daily update (DailyUpdateTicketRequest).
// Percentage is 0 to 100; a nil note is stored as none.
type DailyUpdateTicketRequest struct {
	TicketID   string  `json:"ticketId"`
	Percentage int     `json:"percentage"`
	Note       *string `json:"note,omitempty"`
}

// DailyUpdateRequest is the body that creates, edits or previews a daily update
// (DailyUpdateCreateRequest and DailyUpdateUpdateRequest, which hold the same fields): 1 to 30
// ticket lines in display order, each ticket at most once. An edit sends the full new content,
// not a patch: a line missing from Tickets is removed and a nil focus or questions is cleared.
// The server keeps the lines whose ticket is gone, which have no ticket id to send.
type DailyUpdateRequest struct {
	Tickets   []DailyUpdateTicketRequest `json:"tickets"`
	Focus     *string                    `json:"focus,omitempty"`
	Questions *string                    `json:"questions,omitempty"`
}

// DailyUpdateAuthor is the author of a daily update.
type DailyUpdateAuthor struct {
	UserID    string `json:"userId"`
	Email     string `json:"email"`
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
}

// DailyUpdateItem is one ticket line of a daily update or a preview. Key, title and board are
// frozen when the line is added, and TicketID is nil once the ticket is gone. ProgressSynced is
// true when the line wrote its percentage to the ticket, in a preview when it would. CanOpen is
// true when the ticket exists and the reader is a member of its board.
type DailyUpdateItem struct {
	TicketID       *string `json:"ticketId"`
	BoardID        string  `json:"boardId"`
	BoardName      string  `json:"boardName"`
	TicketKey      string  `json:"ticketKey"`
	TicketTitle    string  `json:"ticketTitle"`
	Percentage     int     `json:"percentage"`
	Note           *string `json:"note"`
	ProgressSynced bool    `json:"progressSynced"`
	CanOpen        bool    `json:"canOpen"`
}

// DailyUpdate is a posted daily update (DailyUpdateResponse). SummaryText is the paste-ready
// text, the same for every reader. EditedAt is nil until the author edits it.
type DailyUpdate struct {
	ID          string            `json:"id"`
	CreatedAt   string            `json:"createdAt"`
	EditedAt    *string           `json:"editedAt"`
	Author      DailyUpdateAuthor `json:"author"`
	Focus       *string           `json:"focus"`
	Questions   *string           `json:"questions"`
	Items       []DailyUpdateItem `json:"items"`
	SummaryText string            `json:"summaryText"`
}

// DailyUpdatePreview is the text a create or edit request would have; nothing is saved.
type DailyUpdatePreview struct {
	Items       []DailyUpdateItem `json:"items"`
	SummaryText string            `json:"summaryText"`
}

// DailyUpdatePage is one page of daily updates, newest first, with the totals the list prints
// under its table.
type DailyUpdatePage struct {
	Updates       []DailyUpdate
	CurrentPage   int
	TotalPages    int
	TotalElements int
}

// DailyUpdateMissingMember is an expected member without a daily update before the deadline.
// PostedAt is nil when the member has not posted that day, else the instant of the member's
// first update of the day, after the deadline.
type DailyUpdateMissingMember struct {
	UserID    string  `json:"userId"`
	Email     string  `json:"email"`
	FirstName string  `json:"firstName"`
	LastName  string  `json:"lastName"`
	PostedAt  *string `json:"postedAt"`
}

// DailyUpdateMissing is who has not posted a daily update in an organization on one
// Europe/Istanbul day. Deadline is that day at 10:30 Istanbul time, and DeadlinePassed whether it
// had passed when the server answered. Members holds first those who have not posted, then those
// who posted late. On a weekend Workday is false and Members is empty.
type DailyUpdateMissing struct {
	OrganizationID string                     `json:"organizationId"`
	Date           string                     `json:"date"`
	Deadline       string                     `json:"deadline"`
	Workday        bool                       `json:"workday"`
	DeadlinePassed bool                       `json:"deadlinePassed"`
	ExpectedCount  int                        `json:"expectedCount"`
	Members        []DailyUpdateMissingMember `json:"members"`
}
