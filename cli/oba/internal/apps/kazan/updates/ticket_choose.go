package updates

import (
	"errors"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ChooseTickets returns the tickets of a new update: those refs names, else the suggested
// tickets. On a terminal (p not nil) the user confirms the suggestions or picks from their open
// tickets instead; without one, no suggestion is an error. me reads the signed-in user's id for
// the pick list.
func ChooseTickets(r *ticket.Resolver, refs []string, p *Prompter, me func() (string, error)) ([]client.Ticket, error) {
	if len(refs) > 0 {
		tickets := make([]client.Ticket, 0, len(refs))
		for _, ref := range refs {
			t, err := ResolveTicket(r, ref)
			if err != nil {
				return nil, err
			}
			tickets = append(tickets, t)
		}
		return tickets, nil
	}

	suggested, err := r.Client().SuggestedUpdateTickets()
	if err != nil {
		return nil, fmt.Errorf("failed to read the suggested tickets: %w", err)
	}
	if p == nil {
		if len(suggested) == 0 {
			return nil, errors.New("no ticket is suggested for you (none assigned to you is IN_PROGRESS): pass --tickets KEY1,KEY2")
		}
		return suggested, nil
	}

	if len(suggested) > 0 {
		_, _ = fmt.Fprintln(p.Out, "Your active tickets:")
		for _, t := range suggested {
			_, _ = fmt.Fprintf(p.Out, "  %s  %s (%d%%)\n", cell(t.Key), cell(t.Title), t.Percentage)
		}
		use, err := p.Confirm(fmt.Sprintf("Use these %d tickets?", len(suggested)))
		if err != nil {
			return nil, err
		}
		if use {
			return suggested, nil
		}
	} else {
		_, _ = fmt.Fprintln(p.Out, "No IN_PROGRESS ticket is assigned to you.")
	}
	userID, err := me()
	if err != nil {
		return nil, err
	}
	return PickMine(r.Client(), *p, userID)
}
