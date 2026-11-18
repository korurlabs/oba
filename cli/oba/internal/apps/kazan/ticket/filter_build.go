package ticket

import (
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// Filters are the filter flags ticket list, all and mine share, as typed. A filter a command
// does not offer stays empty and adds nothing.
type Filters struct {
	Status   string
	Type     string
	Priority string
	Label    string
	Search   string
	DueFrom  string
	DueTo    string
	SortBy   string
	SortDir  string
	Assignee string
	Reviewer string
}

// Check validates the filters that need no request, so that a bad value fails before any call.
func (f Filters) Check() error {
	_, err := f.localQuery()
	return err
}

// localQuery builds the query of the filters that need no lookup: statuses, types, priorities,
// labels, search, the due-date range and sorting.
func (f Filters) localQuery() (url.Values, error) {
	q := url.Values{}
	enums := []struct {
		flag, value, param string
		allowed            []string
	}{
		{"status", f.Status, "statuses", Statuses},
		{"type", f.Type, "types", Types},
		{"priority", f.Priority, "priorities", Priorities},
	}
	for _, e := range enums {
		values, err := enumList(e.flag, e.value, e.allowed)
		if err != nil {
			return nil, err
		}
		for _, v := range values {
			q.Add(e.param, v)
		}
	}
	for _, id := range userinput.SplitCSV(f.Label) {
		q.Add("labelIds", id)
	}
	if f.Search != "" {
		q.Set("search", f.Search)
	}
	from, to := "", ""
	if f.DueFrom != "" {
		date, err := dates.ToAPIDate(f.DueFrom)
		if err != nil {
			return nil, fmt.Errorf("--due-from: %w", err)
		}
		from = date
		q.Set("dueDateStart", date)
	}
	if f.DueTo != "" {
		date, err := dates.ToAPIDate(f.DueTo)
		if err != nil {
			return nil, fmt.Errorf("--due-to: %w", err)
		}
		to = date
		q.Set("dueDateEnd", date)
	}
	// YYYY-MM-DD strings order like the days they name.
	if from != "" && to != "" && from > to {
		return nil, fmt.Errorf("--due-from %s is after --due-to %s", from, to)
	}
	if v := strings.TrimSpace(f.SortBy); v != "" {
		q.Set("sortBy", v)
	}
	if f.SortDir != "" {
		dir, err := EnumValue("sort-dir", f.SortDir, sortOrders)
		if err != nil {
			return nil, err
		}
		q.Set("sortDirection", dir)
	}
	return q, nil
}

// query is localQuery with the --assignee and --reviewer people, each a comma-separated list of
// emails or user ids, resolved to ids.
func (f Filters) query(c *client.Client) (url.Values, error) {
	q, err := f.localQuery()
	if err != nil {
		return nil, err
	}
	people := []struct{ flag, value, param string }{
		{"assignee", f.Assignee, "assigneeIds"},
		{"reviewer", f.Reviewer, "reviewerIds"},
	}
	for _, p := range people {
		if p.value == "" {
			continue
		}
		ids, err := organization.ResolveMemberIDs(c, userinput.SplitCSV(p.value))
		if err != nil {
			return nil, fmt.Errorf("--%s: %w", p.flag, err)
		}
		for _, id := range ids {
			q.Add(p.param, id)
		}
	}
	return q, nil
}
