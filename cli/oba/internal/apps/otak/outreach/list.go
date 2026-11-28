package outreach

import (
	"fmt"
	"io"
	"net/url"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// List reads the outreaches of the organization, newest first, of one relation, one status and
// one campaign when they are not empty.
func List(c *client.Client, relationID, status, campaignID string) ([]client.Outreach, error) {
	q := url.Values{}
	if relationID != "" {
		q.Set("relationId", relationID)
	}
	if campaignID != "" {
		q.Set("campaignId", campaignID)
	}
	if status != "" {
		q.Set("status", strings.ToUpper(strings.TrimSpace(status)))
	}
	return c.ListOutreaches(q)
}

// PrintList writes the outreaches as one table: title, channel, status, contact, the mail
// address or phone it went to, when, from which mailbox (MAIL only), and id.
func PrintList(out io.Writer, outreaches []client.Outreach) error {
	if len(outreaches) == 0 {
		_, _ = fmt.Fprintln(out, "No outreaches.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "TITLE\tTYPE\tSTATUS\tCONTACT\tSENT TO\tSENT AT\tSENDER\tID")
	for _, o := range outreaches {
		contact := "-"
		if o.Contact != nil {
			contact = orDash(o.Contact.Name)
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", orDash(o.Title), orDash(o.Type), orDash(o.Status), contact,
			optional(o.SentTo), optional(o.SentAt), optional(o.MailSenderAddress), orDash(o.ID))
	}
	return w.Flush()
}
