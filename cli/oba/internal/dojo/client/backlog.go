package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// Backlog is the backlog of one board as GET /kaizen/boards/{boardId}/backlog returns it
// (BacklogDetailResponse): the tickets of the board that are in no sprint.
type Backlog struct {
	ID      string          `json:"id"`
	BoardID string          `json:"boardId"`
	Tickets []BacklogTicket `json:"tickets"`
}

// BacklogTicket is the part of a backlog ticket (TicketListResponse) the backlog table shows.
type BacklogTicket struct {
	ID       string `json:"id"`
	Key      string `json:"key"`
	Title    string `json:"title"`
	Status   string `json:"status"`
	Priority string `json:"priority"`
	Type     string `json:"type"`
}

func backlogPath(boardID string) string {
	return boardPath(boardID) + "/backlog"
}

// GetBacklog reads the backlog of board boardID.
func (c *Client) GetBacklog(boardID string) (Backlog, error) {
	body, err := c.Get(backlogPath(boardID))
	if err != nil {
		return Backlog{}, err
	}
	var resp APIResponse[Backlog]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Backlog{}, fmt.Errorf("failed to read the backlog of board %s: %w", boardID, err)
	}
	return resp.Data, nil
}

// AddTicketToBacklog moves ticket ticketID of board boardID to the board's backlog.
func (c *Client) AddTicketToBacklog(boardID, ticketID string) error {
	_, err := c.Post(backlogPath(boardID)+"/tickets/"+url.PathEscape(ticketID), nil)
	return err
}
