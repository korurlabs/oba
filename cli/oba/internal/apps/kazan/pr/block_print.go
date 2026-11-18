package pr

import (
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintCard writes the card text alone, so that stdout can be piped to the clipboard.
func PrintCard(out io.Writer, text string) {
	_, _ = fmt.Fprintln(out, strings.TrimRight(guardrail.Sanitize(text), "\n"))
}

// PrintBlock writes one share: the pull request, status, author and date, then with withTitle
// the title, then reviewers, related tickets, links and id. The Kazan link is left out when the
// card is gone.
func PrintBlock(out io.Writer, s client.PullRequestShare, withTitle bool) {
	date := dates.FormatInstant(s.CreatedAt)
	if s.EditedAt != nil {
		date += " (edited)"
	}
	_, _ = fmt.Fprintf(out, "%s#%d  %s  %s  %s\n", guardrail.OneLine(s.RepositoryFullName), s.Number,
		StatusLabel(s.Status), guardrail.PersonName(s.AuthorFirstName, s.AuthorLastName, s.AuthorID), date)
	if withTitle {
		_, _ = fmt.Fprintln(out, guardrail.OneLine(s.Title))
	}
	_, _ = fmt.Fprintln(out, ReviewerLine(s.Reviewers))
	_, _ = fmt.Fprintln(out, TicketLine(s.RelatedTickets))
	_, _ = fmt.Fprintf(out, "GitHub: %s\n", guardrail.OneLine(s.URL))
	if link, ok := Link(s); ok {
		_, _ = fmt.Fprintf(out, "Kazan: %s\n", guardrail.OneLine(link))
	}
	_, _ = fmt.Fprintf(out, "ID: %s\n", guardrail.OneLine(s.ID))
}

// ReviewerLine is "Reviewers: Ada Lovelace ✓, Bob Stone (pending)", or "Reviewers: none".
func ReviewerLine(reviewers []client.PullRequestShareReviewer) string {
	if len(reviewers) == 0 {
		return "Reviewers: none"
	}
	parts := make([]string, len(reviewers))
	for i, r := range reviewers {
		name := guardrail.PersonName(r.FirstName, r.LastName, r.UserID)
		if r.ApprovedAt != nil {
			parts[i] = name + " ✓"
		} else {
			parts[i] = name + " (pending)"
		}
	}
	return "Reviewers: " + strings.Join(parts, ", ")
}

// TicketLine is "Tickets: WEB-241 Sidebar refactor, KAGI-40 Login", or "Tickets: none". Key and
// title are those stored when the ticket was added.
func TicketLine(tickets []client.PullRequestShareTicket) string {
	if len(tickets) == 0 {
		return "Tickets: none"
	}
	parts := make([]string, len(tickets))
	for i, t := range tickets {
		parts[i] = guardrail.OneLine(strings.TrimSpace(t.TicketKey + " " + t.TicketTitle))
		if parts[i] == "" {
			parts[i] = guardrail.OneLine(t.TicketID)
		}
	}
	return "Tickets: " + strings.Join(parts, ", ")
}
