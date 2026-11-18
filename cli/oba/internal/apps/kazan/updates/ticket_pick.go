package updates

import (
	"errors"
	"fmt"
	"net/url"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// PickMine lets the user choose the update's tickets among the open tickets assigned to userID,
// the signed-in user: the list 'oba kazan ticket mine' shows, without DONE. An empty answer or
// the end of input is ErrCancelled.
func PickMine(c *client.Client, p Prompter, userID string) ([]client.Ticket, error) {
	q := url.Values{}
	q.Add("assigneeIds", userID)
	for _, s := range OpenTicketStatuses {
		q.Add("statuses", s)
	}
	mine, err := c.Unrecorded().ListTickets(q)
	if err != nil {
		return nil, fmt.Errorf("failed to list your tickets: %w", err)
	}
	if len(mine) == 0 {
		return nil, errors.New("no open ticket is assigned to you: pass --tickets KEY1,KEY2")
	}

	_, _ = fmt.Fprintln(p.Out, "Your open tickets:")
	for i, t := range mine {
		_, _ = fmt.Fprintf(p.Out, "  %d. %s  %s (%s, %d%%)\n", i+1, cell(t.Key), cell(t.Title), cell(t.Status), t.Percentage)
	}
	for {
		answer, eof, err := p.ask("Select the tickets of your update (such as 1,3-5 or all): ")
		if err != nil {
			return nil, err
		}
		picks, parseErr := terminal.ParsePicks(answer, len(mine), nil, "")
		if parseErr == nil {
			if len(picks) == 0 {
				return nil, ErrCancelled
			}
			picked := make([]client.Ticket, len(picks))
			for i, number := range picks {
				picked[i] = mine[number-1]
			}
			return picked, nil
		}
		if eof {
			return nil, ErrCancelled
		}
		_, _ = fmt.Fprintf(p.Out, "  %s\n", parseErr)
	}
}
