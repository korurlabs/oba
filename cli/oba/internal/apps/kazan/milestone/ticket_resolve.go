package milestone

import "errors"

// errNoTickets refuses a link or unlink that names no ticket, before any call.
var errNoTickets = errors.New("--tickets is required: ticket keys such as OPS-1,OPS-2, or ids")

// TicketResolver returns the id of the ticket ref names: a key, whose prefix names its board, or
// an id. The ticket package resolves tickets and imports this package to resolve the milestone a
// ticket names, so the jobs here that name tickets take a TicketResolver instead.
type TicketResolver func(ref string) (string, error)

// resolveTickets returns the ids of the tickets refs name, in order.
func resolveTickets(resolve TicketResolver, refs []string) ([]string, error) {
	ids := make([]string, 0, len(refs))
	for _, ref := range refs {
		id, err := resolve(ref)
		if err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, nil
}
