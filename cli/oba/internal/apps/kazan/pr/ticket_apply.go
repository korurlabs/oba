package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// applyTickets applies --tickets, or --remove-tickets then --add-tickets. Removing a ticket
// that is not on the share, adding one that already is, two entries naming one ticket, and a
// result over the limit are refused before any write.
func (e *Edit) applyTickets(c *client.Client, stored []client.PullRequestShareTicket, f EditFlags) error {
	var finder *ticketFinder
	resolve := func(ref string) (string, error) {
		if id, ok := storedTicketID(ref, stored); ok {
			return id, nil
		}
		if finder == nil {
			finder = newTicketFinder(c)
		}
		return finder.id(ref)
	}

	if f.ReplaceTickets {
		ids := make([]string, 0, len(f.Tickets))
		for i, ref := range f.Tickets {
			id, err := resolve(ref)
			if err != nil {
				return fmt.Errorf("--tickets: %w", err)
			}
			if at := indexOfID(ids, id); at >= 0 {
				return fmt.Errorf("--tickets: tickets %s and %s are the same ticket", f.Tickets[at], f.Tickets[i])
			}
			ids = append(ids, id)
			e.labelOnce(id, ref)
		}
		e.TicketIDs = ids
		return nil
	}

	for _, ref := range f.RemoveTickets {
		id, ok := storedTicketID(ref, stored)
		at := indexOfID(e.TicketIDs, id)
		if !ok || at < 0 {
			return fmt.Errorf("--remove-tickets %s: not a related ticket of this share", ref)
		}
		e.TicketIDs = append(e.TicketIDs[:at:at], e.TicketIDs[at+1:]...)
	}

	for _, ref := range f.AddTickets {
		id, err := resolve(ref)
		if err != nil {
			return fmt.Errorf("--add-tickets: %w", err)
		}
		if indexOfID(e.TicketIDs, id) >= 0 {
			return fmt.Errorf("--add-tickets %s: already a related ticket of this share", ref)
		}
		e.TicketIDs = append(e.TicketIDs, id)
		e.labelOnce(id, ref)
	}

	if len(e.TicketIDs) > MaxRelatedTickets {
		return fmt.Errorf("the share would name %d related tickets; a share can name at most %d", len(e.TicketIDs), MaxRelatedTickets)
	}
	return nil
}

// storedTicketID finds a ticket stored on the share by its key or its id, case ignored. Stored
// tickets match without a request, so a ticket that is deleted or on a board the author left can
// still be kept or removed, as the API allows.
func storedTicketID(ref string, stored []client.PullRequestShareTicket) (string, bool) {
	for _, t := range stored {
		if strings.EqualFold(t.TicketID, ref) || strings.EqualFold(t.TicketKey, ref) {
			return t.TicketID, true
		}
	}
	return "", false
}

// labelOnce labels a ticket id that has no label yet with the ref the user typed: a key in upper
// case, an id as given.
func (e *Edit) labelOnce(id, ref string) {
	if _, known := e.TicketLabels[strings.ToLower(id)]; known {
		return
	}
	if !userinput.IsID(ref) {
		ref = strings.ToUpper(ref)
	}
	e.TicketLabels[strings.ToLower(id)] = ref
}
