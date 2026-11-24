package ticket

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// titleWidth is the most of a title the table shows.
const titleWidth = 50

// PrintTable writes tickets as a table with a count line. In a list read with a search, the API
// lists a ticket when its own text or a sub-ticket nested under it matches, so the sub-tickets on
// the way to a match are written under their ticket, indented, and counted apart.
func PrintTable(out io.Writer, tickets []client.Ticket) error {
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "KEY\tTITLE\tSTATUS\tPRIORITY\tASSIGNEE\tLOCATION")
	subTickets := 0
	for _, t := range tickets {
		printRow(w, t, cell(t.Key))
		subTickets += printSearchedSubTickets(w, t.SubTickets, 1)
	}
	if err := w.Flush(); err != nil {
		return fmt.Errorf("failed to write the tickets: %w", err)
	}
	if subTickets > 0 {
		_, _ = fmt.Fprintf(out, "\nShowing %d tickets and %d sub-tickets that match the search\n", len(tickets), subTickets)
		return nil
	}
	_, _ = fmt.Fprintf(out, "\nShowing %d tickets\n", len(tickets))
	return nil
}

// printSearchedSubTickets writes the sub-tickets on the way to a search match, each under its
// parent and indented by depth, and returns how many it wrote.
func printSearchedSubTickets(w io.Writer, subTickets []client.Ticket, depth int) int {
	written := 0
	for _, sub := range subTickets {
		if !onSearchPath(sub) {
			continue
		}
		printRow(w, sub, strings.Repeat("  ", depth-1)+"└ "+cell(sub.Key))
		written += 1 + printSearchedSubTickets(w, sub.SubTickets, depth+1)
	}
	return written
}

// onSearchPath reports whether the ticket matches the list's search, or has a sub-ticket at any
// depth that does. A ticket of a list read without a search carries no mark and is not.
func onSearchPath(t client.Ticket) bool {
	if t.SearchMatch == nil {
		return false
	}
	if *t.SearchMatch {
		return true
	}
	for _, sub := range t.SubTickets {
		if onSearchPath(sub) {
			return true
		}
	}
	return false
}

func printRow(w io.Writer, t client.Ticket, key string) {
	assignee := ""
	if len(t.Assignees) > 0 {
		assignee = personName(t.Assignees[0])
	}
	location := "-"
	if t.Sprint != nil {
		location = cell(t.Sprint.Name)
	} else if t.Backlog != nil {
		location = "Backlog"
	}
	_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n",
		key, shorten(cell(t.Title), titleWidth), t.Status, t.Priority, assignee, location)
}

// shorten cuts text to width runes, ending in "..." when it was longer.
func shorten(text string, width int) string {
	runes := []rune(text)
	if len(runes) <= width {
		return text
	}
	return string(runes[:width-3]) + "..."
}

func personName(p client.TicketPerson) string {
	return cell(p.FirstName + " " + p.LastName)
}
