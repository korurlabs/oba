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

// PrintTable writes tickets as a table with a count line. With a search, the API lists a ticket
// when its own title or key, or that of a sub-ticket nested under it, contains the text, so the
// sub-tickets on the way to a match are written under their ticket, indented, and counted apart.
func PrintTable(out io.Writer, tickets []client.Ticket, search string) error {
	search = strings.ToLower(strings.TrimSpace(search))
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "KEY\tTITLE\tSTATUS\tPRIORITY\tASSIGNEE\tLOCATION")
	subTickets := 0
	for _, t := range tickets {
		printRow(w, t, cell(t.Key))
		if search != "" {
			subTickets += printSearchedSubTickets(w, t.SubTickets, search, 1)
		}
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

// printSearchedSubTickets writes the sub-tickets on the way to a match of search, lowercased,
// each under its parent and indented by depth, and returns how many it wrote.
func printSearchedSubTickets(w io.Writer, subTickets []client.Ticket, search string, depth int) int {
	written := 0
	for _, sub := range subTickets {
		if !onSearchPath(sub, search) {
			continue
		}
		printRow(w, sub, strings.Repeat("  ", depth-1)+"└ "+cell(sub.Key))
		written += 1 + printSearchedSubTickets(w, sub.SubTickets, search, depth+1)
	}
	return written
}

// onSearchPath reports whether the ticket's title or key contains search, lowercased, or that of
// a sub-ticket at any depth does.
func onSearchPath(t client.Ticket, search string) bool {
	if strings.Contains(strings.ToLower(t.Title), search) || strings.Contains(strings.ToLower(t.Key), search) {
		return true
	}
	for _, sub := range t.SubTickets {
		if onSearchPath(sub, search) {
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
