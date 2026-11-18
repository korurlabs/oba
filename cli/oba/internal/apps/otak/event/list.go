package event

import (
	"fmt"
	"io"
	"net/url"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	queryPeriod = "period"
	queryToday  = "today"
	querySearch = "search"

	// periodUpcoming and periodPast are the values of the API's EventPeriod.
	periodUpcoming = "UPCOMING"
	periodPast     = "PAST"

	emptyCell = "-"
)

// ListQuery narrows an event list. Period is "upcoming" or "past", case ignored, or empty for
// every event; Today is the user's calendar day, YYYY-MM-DD, against which the API splits
// upcoming from past, since the server cannot know the user's day; Search is a part of the
// name, case ignored.
type ListQuery struct {
	Period string
	Today  string
	Search string
}

// List reads every event that matches q. An unknown period is refused before any call.
func List(c *client.Client, q ListQuery) ([]client.Event, error) {
	query := url.Values{}
	period, err := matchPeriod(q.Period)
	if err != nil {
		return nil, err
	}
	if period != "" {
		query.Set(queryPeriod, period)
		query.Set(queryToday, q.Today)
	}
	if search := strings.TrimSpace(q.Search); search != "" {
		query.Set(querySearch, search)
	}
	return c.ListEvents(query)
}

// matchPeriod maps the period the user gave to the API's spelling; empty stays empty.
func matchPeriod(given string) (string, error) {
	value := strings.TrimSpace(given)
	switch {
	case value == "":
		return "", nil
	case strings.EqualFold(value, periodUpcoming):
		return periodUpcoming, nil
	case strings.EqualFold(value, periodPast):
		return periodPast, nil
	}
	return "", fmt.Errorf("unknown period %q; allowed: upcoming, past", value)
}

// PrintList writes the events as a table: name, type, start, end, place (city and country), id.
// Every cell is sanitized onto one line, since names and places are typed by users.
func PrintList(out io.Writer, events []client.Event) error {
	if len(events) == 0 {
		_, _ = fmt.Fprintln(out, "No events.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tTYPE\tSTART\tEND\tPLACE\tID")
	for _, ev := range events {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n", cell(ev.Name), cell(ev.Type), cell(ev.StartDate),
			optionalCell(ev.EndDate), place(ev.City, ev.Country), cell(ev.ID))
	}
	return w.Flush()
}

// place is "city, country", either part alone when the other is empty, or "-" when both are.
func place(city, country *string) string {
	var parts []string
	for _, value := range []*string{city, country} {
		if value == nil {
			continue
		}
		if clean := guardrail.OneLine(*value); clean != "" {
			parts = append(parts, clean)
		}
	}
	if len(parts) == 0 {
		return emptyCell
	}
	return strings.Join(parts, ", ")
}

func optionalCell(value *string) string {
	if value == nil {
		return emptyCell
	}
	return cell(*value)
}

// cell is value on one line with every terminal control removed, or "-" when nothing is left.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}
