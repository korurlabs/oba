package updates

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ResolveTicket returns the ticket ref names, a key or an id, with the progress a new line
// starts from. A key names its own board, so tickets of every board can be on one update; an id
// carries no board and is read on --board or the default board. The reads are lookups and stay
// out of the --json output.
func ResolveTicket(r *ticket.Resolver, ref string) (client.Ticket, error) {
	target, err := r.Ticket(ref)
	if err != nil {
		if userinput.IsID(ref) {
			return client.Ticket{}, fmt.Errorf("ticket %s is an id, which is looked up on a board; pass its key instead: %w", ref, err)
		}
		return client.Ticket{}, err
	}
	if target.Found != nil {
		return *target.Found, nil
	}
	t, err := r.Client().GetTicket(target.BoardID, target.ID)
	if err != nil {
		return client.Ticket{}, fmt.Errorf("failed to read ticket %s: %w", target.Label(), err)
	}
	return t.Ticket, nil
}
