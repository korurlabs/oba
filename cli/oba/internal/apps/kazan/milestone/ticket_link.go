package milestone

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// TicketsChange attaches or detaches tickets. Tickets is the --tickets list of keys or ids;
// ResolveTicket turns each into a ticket id, after the milestone is found.
type TicketsChange struct {
	Milestone     string
	Tickets       string
	ResolveTicket TicketResolver
}

// LinkTickets attaches the tickets of change to its milestone, reachable from board boardID. It
// returns the milestone with its new progress and the number of tickets sent.
func LinkTickets(c *client.Client, boardID string, change TicketsChange) (client.Milestone, int, error) {
	return changeTickets(c, boardID, change, c.LinkMilestoneTickets)
}

func changeTickets(c *client.Client, boardID string, change TicketsChange,
	send func(boardID, id string, ticketIDs []string) (client.Milestone, error)) (client.Milestone, int, error) {
	refs := userinput.SplitCSV(change.Tickets)
	if len(refs) == 0 {
		return client.Milestone{}, 0, errNoTickets
	}
	m, err := Resolve(c, boardID, change.Milestone)
	if err != nil {
		return client.Milestone{}, 0, err
	}
	ids, err := resolveTickets(change.ResolveTicket, refs)
	if err != nil {
		return client.Milestone{}, 0, err
	}
	updated, err := send(owner(m, boardID), m.ID, ids)
	return updated, len(ids), err
}
