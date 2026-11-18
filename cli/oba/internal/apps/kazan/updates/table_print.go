package updates

import (
	"fmt"
	"io"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// emptyList is printed for a page without updates.
const emptyList = "No updates found."

// PrintPage prints a page of updates as a table, DATE in the viewer's zone, with a line naming
// the page when there are more.
func PrintPage(out io.Writer, page client.DailyUpdatePage) error {
	if len(page.Updates) == 0 {
		_, _ = fmt.Fprintln(out, emptyList)
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "DATE\tAUTHOR\tTICKETS\tID")
	for _, u := range page.Updates {
		date := dates.FormatInstant(u.CreatedAt)
		if u.EditedAt != nil {
			date += " (edited)"
		}
		keys := make([]string, len(u.Items))
		for i, item := range u.Items {
			keys[i] = cell(item.TicketKey)
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", date, authorName(u.Author), strings.Join(keys, ", "), cell(u.ID))
	}
	if err := w.Flush(); err != nil {
		return fmt.Errorf("failed to print the updates: %w", err)
	}
	if page.TotalPages > 1 {
		_, _ = fmt.Fprintf(out, "\nPage %d of %d (%d updates). Use --page for more.\n", page.CurrentPage, page.TotalPages, page.TotalElements)
	}
	return nil
}

// authorName is the author's full name, else the email.
func authorName(a client.DailyUpdateAuthor) string {
	return guardrail.PersonName(a.FirstName, a.LastName, a.Email)
}
