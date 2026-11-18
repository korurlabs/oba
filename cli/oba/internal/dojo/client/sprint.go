package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// RelocationDelete is the TicketRelocationAction value that deletes the tickets of a completed or
// deleted sprint; bulk move refuses it, so it is not among the moves of ticket_models.go.
const RelocationDelete = "DELETE"

// Sprint is one sprint of a board. GET /kaizen/boards/{boardId}/sprints (SprintListResponse)
// returns id, name, slug, status, dates and createdAt; goal, boards and updatedAt come from the
// detail. Dates are RFC 3339 instants.
type Sprint struct {
	ID        string        `json:"id"`
	Name      string        `json:"name"`
	Slug      string        `json:"slug"`
	Goal      *string       `json:"goal"`
	Status    string        `json:"status"`
	StartDate *string       `json:"startDate"`
	EndDate   *string       `json:"endDate"`
	Boards    []SprintBoard `json:"boards"`
	CreatedAt string        `json:"createdAt"`
	UpdatedAt string        `json:"updatedAt"`
}

// SprintBoard is a board a sprint is linked to, as the sprint detail lists it.
type SprintBoard struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Prefix string `json:"prefix"`
}

// SprintCreateRequest is the body of POST /kaizen/boards/{boardId}/sprints. ChildBoardIDs links
// the new sprint to descendant boards in the same request; the API rolls the whole create back
// when one of them cannot be linked.
type SprintCreateRequest struct {
	Name          string   `json:"name"`
	Goal          *string  `json:"goal,omitempty"`
	StartDate     *string  `json:"startDate,omitempty"`
	EndDate       *string  `json:"endDate,omitempty"`
	ChildBoardIDs []string `json:"childBoardIds,omitempty"`
}

// SprintChildBoard is a descendant board a new sprint can be linked to. CanLink is true when the
// user is ADMIN or OWNER of it.
type SprintChildBoard struct {
	ID               string  `json:"id"`
	Name             string  `json:"name"`
	Prefix           string  `json:"prefix"`
	Color            *string `json:"color"`
	ParentBoardID    *string `json:"parentBoardId"`
	Depth            int     `json:"depth"`
	CanLink          bool    `json:"canLink"`
	ActiveSprintID   *string `json:"activeSprintId"`
	ActiveSprintName *string `json:"activeSprintName"`
}

// SprintUpdateRequest is the body of PUT /kaizen/boards/{boardId}/sprints/{id}; a nil field is
// left unchanged. A sprint of a repeating series is named after its series, so the API refuses
// Name for it and takes SeriesName, which renames the series and its unfinished sprints.
type SprintUpdateRequest struct {
	Name       *string `json:"name,omitempty"`
	Goal       *string `json:"goal,omitempty"`
	StartDate  *string `json:"startDate,omitempty"`
	EndDate    *string `json:"endDate,omitempty"`
	SeriesName *string `json:"seriesName,omitempty"`
}

// SprintCompleteRequest says where the unfinished tickets of a completed sprint go.
// TargetSprintID is required for RelocationMoveToSprint; TargetBoardID moves them to another
// board.
type SprintCompleteRequest struct {
	TicketAction   string  `json:"ticketAction"`
	TargetSprintID *string `json:"targetSprintId,omitempty"`
	TargetBoardID  *string `json:"targetBoardId,omitempty"`
}

// SprintBulkLinkRequest links a sprint to the listed boards.
type SprintBulkLinkRequest struct {
	BoardIDs []string `json:"boardIds"`
}

// SprintUnlinkRequest says what happens to a sprint's tickets when it is unlinked from a board:
// moved by Transfer, or deleted with DeleteTickets. The API deletes the sprint when no other
// board links it.
type SprintUnlinkRequest struct {
	Transfer      *SprintUnlinkTransfer `json:"transfer,omitempty"`
	DeleteTickets *bool                 `json:"deleteTickets,omitempty"`
}

// SprintUnlinkTransfer is the destination of a sprint's tickets on unlink: a sprint of BoardID
// when SprintID is set, else BoardID's backlog.
type SprintUnlinkTransfer struct {
	BoardID  string  `json:"boardId"`
	SprintID *string `json:"sprintId,omitempty"`
}

func sprintsPath(boardID string) string {
	return boardPath(boardID) + "/sprints"
}

func sprintPath(boardID, id string) string {
	return sprintsPath(boardID) + "/" + url.PathEscape(id)
}

// ListSprints reads every sprint of board boardID. The API answers with one plain list, not
// pages.
func (c *Client) ListSprints(boardID string) ([]Sprint, error) {
	body, err := c.Get(sprintsPath(boardID))
	if err != nil {
		return nil, err
	}
	var resp APIResponse[[]Sprint]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to read the sprints of board %s: %w", boardID, err)
	}
	return resp.Data, nil
}

// GetSprint reads sprint id of board boardID with its goal and linked boards.
func (c *Client) GetSprint(boardID, id string) (Sprint, error) {
	body, err := c.Get(sprintPath(boardID, id))
	if err != nil {
		return Sprint{}, err
	}
	return parseData[Sprint](body, "sprint "+id)
}

// CreateSprint creates a sprint on board boardID and returns it.
func (c *Client) CreateSprint(boardID string, req SprintCreateRequest) (Sprint, error) {
	body, err := c.Post(sprintsPath(boardID), req)
	if err != nil {
		return Sprint{}, err
	}
	return parseData[Sprint](body, "the created sprint")
}

// UpdateSprint changes the given fields of sprint id of board boardID and returns it.
func (c *Client) UpdateSprint(boardID, id string, req SprintUpdateRequest) (Sprint, error) {
	body, err := c.Put(sprintPath(boardID, id), req)
	if err != nil {
		return Sprint{}, err
	}
	return parseData[Sprint](body, "the updated sprint")
}

// StartSprint starts sprint id of board boardID.
func (c *Client) StartSprint(boardID, id string) error {
	_, err := c.Post(sprintPath(boardID, id)+"/start", nil)
	return err
}

// CompleteSprint completes sprint id of board boardID and moves or deletes its unfinished
// tickets as req says.
func (c *Client) CompleteSprint(boardID, id string, req SprintCompleteRequest) error {
	_, err := c.Post(sprintPath(boardID, id)+"/complete", req)
	return err
}

// LinkSprint links sprint id of board boardID to the boards of req.
func (c *Client) LinkSprint(boardID, id string, req SprintBulkLinkRequest) error {
	_, err := c.Post(sprintPath(boardID, id)+"/bulk-link", req)
	return err
}

// UnlinkSprint unlinks sprint id from board boardID and moves or deletes its tickets as req says.
func (c *Client) UnlinkSprint(boardID, id string, req SprintUnlinkRequest) error {
	_, err := c.Post(sprintPath(boardID, id)+"/unlink", req)
	return err
}

// DeleteSprint deletes sprint id of board boardID; query holds ticketAction and, for a move to a
// sprint, targetSprintId.
func (c *Client) DeleteSprint(boardID, id string, query url.Values) error {
	_, err := c.Delete(withQuery(sprintPath(boardID, id), query))
	return err
}

// RestoreSprint restores deleted sprint id of board boardID.
func (c *Client) RestoreSprint(boardID, id string) error {
	_, err := c.Post(sprintPath(boardID, id)+"/restore", nil)
	return err
}

// ListSprintChildBoards lists the descendant boards of boardID that the user is a member of,
// depth first: the boards a new sprint of boardID can also be linked to.
func (c *Client) ListSprintChildBoards(boardID string) ([]SprintChildBoard, error) {
	body, err := c.Get(sprintsPath(boardID) + "/child-boards")
	if err != nil {
		return nil, err
	}
	return parseData[[]SprintChildBoard](body, fmt.Sprintf("the child boards of board %s", boardID))
}
