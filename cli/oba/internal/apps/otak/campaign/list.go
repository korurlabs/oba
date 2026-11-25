package campaign

import (
	"fmt"
	"io"
	"net/url"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	queryStatus  = "status"
	queryChannel = "channel"
	querySearch  = "search"

	emptyCell = "-"
)

// statuses and channels are the values of the API's CampaignStatus and CampaignChannel, in the
// order of the backend enums.
var (
	statuses = []string{"DRAFT", "SCHEDULED", "PUBLISHED", "ARCHIVED"}
	channels = []string{"LINKEDIN", "X", "NEWSLETTER"}
)

// ListQuery narrows a campaign list. Status and Channel are one value of the API's enum, case
// ignored, or empty for every one; Search is a part of the name, case ignored.
type ListQuery struct {
	Status  string
	Channel string
	Search  string
}

// List reads every campaign that matches q in one call, since the API does not page campaigns. An
// unknown status or channel is refused before any call.
func List(c *client.Client, q ListQuery) ([]client.Campaign, error) {
	query := url.Values{}
	status, err := matchEnum(queryStatus, q.Status, statuses)
	if err != nil {
		return nil, err
	}
	if status != "" {
		query.Set(queryStatus, status)
	}
	channel, err := matchEnum(queryChannel, q.Channel, channels)
	if err != nil {
		return nil, err
	}
	if channel != "" {
		query.Set(queryChannel, channel)
	}
	if search := strings.TrimSpace(q.Search); search != "" {
		query.Set(querySearch, search)
	}
	return c.ListCampaigns(query)
}

// matchEnum maps the value the user gave to the API's spelling; empty stays empty.
func matchEnum(name, given string, allowed []string) (string, error) {
	value := strings.TrimSpace(given)
	if value == "" {
		return "", nil
	}
	for _, candidate := range allowed {
		if strings.EqualFold(value, candidate) {
			return candidate, nil
		}
	}
	return "", fmt.Errorf("unknown %s %q; allowed: %s", name, value, strings.ToLower(strings.Join(allowed, ", ")))
}

// PrintList writes the campaigns as a table: name, channel, status, planned and published moment
// in this machine's zone, the languages that have a text, owner, id. Every cell is sanitized onto
// one line, since names are typed by users.
func PrintList(out io.Writer, campaigns []client.Campaign) error {
	if len(campaigns) == 0 {
		_, _ = fmt.Fprintln(out, "No campaigns.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tCHANNEL\tSTATUS\tPLANNED\tPUBLISHED\tLANGUAGES\tOWNER\tID")
	for _, cp := range campaigns {
		owner := ""
		if cp.Owner != nil {
			owner = cp.Owner.Name
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", cell(cp.Name), cell(cp.Channel), cell(cp.Status),
			instantCell(cp.PlannedAt), instantCell(cp.PublishedAt), cell(strings.Join(cp.Languages, " ")), cell(owner), cell(cp.ID))
	}
	return w.Flush()
}

func instantCell(value *string) string {
	if value == nil || *value == "" {
		return emptyCell
	}
	return cell(dates.FormatInstant(*value))
}

// cell is value on one line with every terminal control removed, or "-" when nothing is left.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}
