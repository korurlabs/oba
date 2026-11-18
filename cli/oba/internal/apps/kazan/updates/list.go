package updates

import (
	"fmt"
	"net/url"
	"strconv"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// ListInput is what update list reads: the authors ("me", emails or user ids) and boards (names,
// key prefixes or ids), both comma separated, the inclusive report days and the page.
type ListInput struct {
	Author string
	Boards string
	From   string
	To     string
	Page   int
	Amount int
}

// localQuery builds the query of the flags that need no lookup, so that a bad value fails before
// any request.
func (in ListInput) localQuery() (url.Values, error) {
	if in.Page < 1 {
		return nil, fmt.Errorf("--page must be 1 or more, got %d", in.Page)
	}
	if in.Amount < 1 || in.Amount > MaxPageSize {
		return nil, fmt.Errorf("--amount must be from 1 to %d, got %d", MaxPageSize, in.Amount)
	}
	q := url.Values{}
	q.Set("page", strconv.Itoa(in.Page))
	q.Set("amount", strconv.Itoa(in.Amount))
	from, to := "", ""
	if in.From != "" {
		day, err := dates.ToAPIDate(in.From)
		if err != nil {
			return nil, fmt.Errorf("--from: %w", err)
		}
		from = day
		q.Set("from", day)
	}
	if in.To != "" {
		day, err := dates.ToAPIDate(in.To)
		if err != nil {
			return nil, fmt.Errorf("--to: %w", err)
		}
		to = day
		q.Set("to", day)
	}
	// YYYY-MM-DD strings order like the days they name.
	if from != "" && to != "" && from > to {
		return nil, fmt.Errorf("--from %s is after --to %s", from, to)
	}
	return q, nil
}

// Check validates the flags that need no request.
func (in ListInput) Check() error {
	_, err := in.localQuery()
	return err
}

// List reads one page of the updates the input selects, through c, which records the result.
// me reads the signed-in user's id when --author names "me".
func List(c *client.Client, r *ticket.Resolver, in ListInput, me func() (string, error)) (client.DailyUpdatePage, error) {
	q, err := in.localQuery()
	if err != nil {
		return client.DailyUpdatePage{}, err
	}
	if in.Author != "" {
		ids, err := organization.ResolveAuthorIDs(r.Client(), userinput.SplitCSV(in.Author), me)
		if err != nil {
			return client.DailyUpdatePage{}, fmt.Errorf("--author: %w", err)
		}
		for _, id := range ids {
			q.Add("authorIds", id)
		}
	}
	for _, ref := range userinput.SplitCSV(in.Boards) {
		id, err := r.Board(ref)
		if err != nil {
			return client.DailyUpdatePage{}, fmt.Errorf("--boards: %w", err)
		}
		q.Add("boardIds", id)
	}
	page, err := c.ListDailyUpdates(q)
	if err != nil {
		return client.DailyUpdatePage{}, fmt.Errorf("failed to list updates: %w", err)
	}
	return page, nil
}
