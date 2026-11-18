package pr

import (
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ticketFinder resolves related tickets for one run: an id as it is, without a call, so that
// the API checks it; a key on the board its prefix names, at any depth of the board tree, so
// that tickets of every board can be named. The board tree is read once per run, and every
// lookup stays out of the --json output.
type ticketFinder struct {
	r *ticket.Resolver
}

func newTicketFinder(c *client.Client) *ticketFinder {
	return &ticketFinder{r: ticket.NewResolver(c, "", io.Discard)}
}

func (f *ticketFinder) id(ref string) (string, error) {
	if userinput.IsID(ref) {
		return strings.TrimSpace(ref), nil
	}
	t, err := f.r.Ticket(ref)
	if err != nil {
		return "", err
	}
	return t.ID, nil
}

// ResolveTickets resolves related tickets given by key or id to ticket ids, in the order given.
// Two entries naming the same ticket, such as a key and its id, are refused.
func ResolveTickets(c *client.Client, refs []string) ([]string, error) {
	f := newTicketFinder(c)
	ids := make([]string, 0, len(refs))
	for _, ref := range refs {
		id, err := f.id(ref)
		if err != nil {
			return nil, err
		}
		if at := indexOfID(ids, id); at >= 0 {
			return nil, fmt.Errorf("tickets %s and %s are the same ticket", refs[at], ref)
		}
		ids = append(ids, id)
	}
	return ids, nil
}

// indexOfID returns the position of id in ids, case ignored, or -1.
func indexOfID(ids []string, id string) int {
	for i, v := range ids {
		if strings.EqualFold(v, id) {
			return i
		}
	}
	return -1
}
