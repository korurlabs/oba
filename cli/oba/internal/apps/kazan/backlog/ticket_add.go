package backlog

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// AddTicket moves ticket ticketID of board boardID into the board's backlog.
func AddTicket(c *client.Client, boardID, ticketID string) error {
	ticketID = strings.TrimSpace(ticketID)
	if ticketID == "" {
		return errors.New("no ticket given; name the ticket by its id")
	}
	return c.AddTicketToBacklog(boardID, ticketID)
}
