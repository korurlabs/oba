package ticket

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// titleWidth is the most of a title the table shows.
const titleWidth = 50

// PrintTable writes tickets as a table with a count line.
func PrintTable(out io.Writer, tickets []client.Ticket) error {
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "KEY\tTITLE\tSTATUS\tPRIORITY\tASSIGNEE\tLOCATION")
	for _, t := range tickets {
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
			cell(t.Key), shorten(cell(t.Title), titleWidth), t.Status, t.Priority, assignee, location)
	}
	if err := w.Flush(); err != nil {
		return fmt.Errorf("failed to write the tickets: %w", err)
	}
	_, _ = fmt.Fprintf(out, "\nShowing %d tickets\n", len(tickets))
	return nil
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
