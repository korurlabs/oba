package campaign

import (
	"fmt"
	"io"
	"net/url"
	"strconv"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	querySearch  = "search"
	queryEventID = "eventId"

	emptyCell = "-"
)

// ListQuery narrows a campaign list. Search is a part of the name, case ignored; EventID keeps
// the campaigns of one event. Empty means no filter.
type ListQuery struct {
	Search  string
	EventID string
}

// List reads every campaign that matches q in one call, since the API does not page campaigns.
func List(c *client.Client, q ListQuery) ([]client.Campaign, error) {
	query := url.Values{}
	if search := strings.TrimSpace(q.Search); search != "" {
		query.Set(querySearch, search)
	}
	if eventID := strings.TrimSpace(q.EventID); eventID != "" {
		query.Set(queryEventID, eventID)
	}
	return c.ListCampaigns(query)
}

// PrintList writes the campaigns as a table: name, start and end date, number of contents,
// owner, id. Every cell is sanitized onto one line, since names are typed by users.
func PrintList(out io.Writer, campaigns []client.Campaign) error {
	if len(campaigns) == 0 {
		_, _ = fmt.Fprintln(out, "No campaigns.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tSTART\tEND\tCONTENTS\tOWNER\tID")
	for _, cp := range campaigns {
		owner := ""
		if cp.Owner != nil {
			owner = cp.Owner.Name
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n", cell(cp.Name), optionalCell(cp.StartDate), optionalCell(cp.EndDate),
			strconv.Itoa(cp.ContentCount), cell(owner), cell(cp.ID))
	}
	return w.Flush()
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
