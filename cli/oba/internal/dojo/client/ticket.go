package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// ticketsPath is the collection of the tickets of every board the user is a member of.
const ticketsPath = kazanPath + "/tickets"

func boardTicketsPath(boardID string) string {
	return boardPath(boardID) + "/tickets"
}

func boardTicketPath(boardID, ticketID string) string {
	return boardTicketsPath(boardID) + "/" + url.PathEscape(ticketID)
}

func withQuery(path string, query url.Values) string {
	if len(query) == 0 {
		return path
	}
	return path + "?" + query.Encode()
}

// ListBoardTickets reads the tickets of board boardID that query selects. The API needs a
// sprintId or a backlogId and answers with the whole list, not pages.
func (c *Client) ListBoardTickets(boardID string, query url.Values) ([]Ticket, error) {
	body, err := c.Get(withQuery(boardTicketsPath(boardID), query))
	if err != nil {
		return nil, err
	}
	return parseData[[]Ticket](body, "the tickets")
}

// ListTickets reads the tickets that query selects on every board the user is a member of, or
// on the boards its boardIds name. The API answers with the whole list, not pages.
func (c *Client) ListTickets(query url.Values) ([]Ticket, error) {
	body, err := c.Get(withQuery(ticketsPath, query))
	if err != nil {
		return nil, err
	}
	return parseData[[]Ticket](body, "the tickets")
}

// GetTicketByKey reads the ticket with the exact key, such as OPS-42. The API searches only the
// boards the user is a member of, so a 404 also means no access.
func (c *Client) GetTicketByKey(key string) (Ticket, error) {
	body, err := c.Get(ticketsPath + "/by-key/" + url.PathEscape(key))
	if err != nil {
		return Ticket{}, err
	}
	return parseData[Ticket](body, fmt.Sprintf("ticket %s", key))
}

// GetTicket reads ticket ticketID of board boardID with all its fields.
func (c *Client) GetTicket(boardID, ticketID string) (TicketDetail, error) {
	body, err := c.Get(boardTicketPath(boardID, ticketID))
	if err != nil {
		return TicketDetail{}, err
	}
	return parseData[TicketDetail](body, fmt.Sprintf("ticket %s", ticketID))
}

// CreateTicket creates a ticket on board boardID and returns it as the API sent it and read.
func (c *Client) CreateTicket(boardID string, req TicketCreateRequest) (Ticket, json.RawMessage, error) {
	body, err := c.Post(boardTicketsPath(boardID), req)
	if err != nil {
		return Ticket{}, nil, err
	}
	var resp APIResponse[json.RawMessage]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Ticket{}, nil, fmt.Errorf("the ticket was created, but the response could not be read: %w", err)
	}
	var created Ticket
	if err := json.Unmarshal(resp.Data, &created); err != nil {
		return Ticket{}, nil, fmt.Errorf("the ticket was created, but the response could not be read: %w", err)
	}
	return created, resp.Data, nil
}

// UpdateTicket changes the given fields of ticket ticketID and returns the ticket.
func (c *Client) UpdateTicket(boardID, ticketID string, req TicketUpdateRequest) (Ticket, error) {
	body, err := c.Put(boardTicketPath(boardID, ticketID), req)
	if err != nil {
		return Ticket{}, err
	}
	return parseData[Ticket](body, "the updated ticket")
}

// DeleteTicket deletes ticket ticketID of board boardID; it can be restored.
func (c *Client) DeleteTicket(boardID, ticketID string) error {
	_, err := c.Delete(boardTicketPath(boardID, ticketID))
	return err
}

// RestoreTicket restores the deleted ticket ticketID of board boardID.
func (c *Client) RestoreTicket(boardID, ticketID string) error {
	_, err := c.Post(boardTicketPath(boardID, ticketID)+"/restore", nil)
	return err
}

// MoveTicket moves ticket ticketID to a sprint or the backlog of its own board.
func (c *Client) MoveTicket(boardID, ticketID string, req TicketMoveRequest) error {
	_, err := c.Post(boardTicketPath(boardID, ticketID)+"/move", req)
	return err
}

// BulkMoveTickets moves tickets to a sprint or the backlog of board boardID, the destination.
func (c *Client) BulkMoveTickets(boardID string, req TicketBulkMoveRequest) (TicketBulkMoveResult, error) {
	body, err := c.Post(boardTicketsPath(boardID)+"/bulk-move", req)
	if err != nil {
		return TicketBulkMoveResult{}, err
	}
	return parseData[TicketBulkMoveResult](body, "the result of the move")
}

// OrderTicket moves ticket ticketID to position order within its sprint or backlog.
func (c *Client) OrderTicket(boardID, ticketID string, order int) error {
	_, err := c.Put(boardTicketPath(boardID, ticketID)+"/order", ticketOrderRequest{Order: order})
	return err
}
