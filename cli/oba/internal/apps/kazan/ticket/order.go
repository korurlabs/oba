package ticket

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Order moves the ticket ref names to position order within its own sprint or backlog.
func Order(c *client.Client, r *Resolver, ref string, order int) (Target, error) {
	target, err := r.Ticket(ref)
	if err != nil {
		return Target{}, err
	}
	if err := c.OrderTicket(target.BoardID, target.ID, order); err != nil {
		return Target{}, fmt.Errorf("failed to reorder ticket: %w", err)
	}
	return target, nil
}
