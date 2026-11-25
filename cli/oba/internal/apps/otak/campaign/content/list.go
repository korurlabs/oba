package content

import (
	"fmt"
	"io"
	"text/tabwriter"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	emptyCell = "-"

	// textCellRunes is how much of a text the list shows; the whole text is in the JSON result.
	textCellRunes = 40
)

// List reads every content of campaign campaignID in the API's agenda order.
func List(c *client.Client, campaignID string) ([]client.CampaignContent, error) {
	return c.ListCampaignContents(campaignID)
}

// PrintList writes the contents as a table: status, channel, language, legal entity, planned and
// published moment in this machine's zone, the start of the text, id. Every cell is sanitized
// onto one line, since texts are typed by users.
func PrintList(out io.Writer, contents []client.CampaignContent) error {
	if len(contents) == 0 {
		_, _ = fmt.Fprintln(out, "No contents.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "STATUS\tCHANNEL\tLANGUAGE\tLEGAL ENTITY\tPLANNED\tPUBLISHED\tTEXT\tID")
	for _, cc := range contents {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", cell(cc.Status), cell(cc.Channel), cell(cc.Language),
			cell(cc.LegalEntityShortName), instantCell(cc.PlannedAt), instantCell(cc.PublishedAt), textCell(cc.Text), cell(cc.ID))
	}
	return w.Flush()
}

func instantCell(value *string) string {
	if value == nil || *value == "" {
		return emptyCell
	}
	return cell(dates.FormatInstant(*value))
}

// textCell is the start of text on one line, cut after textCellRunes runes with "..." appended.
func textCell(text string) string {
	clean := cell(text)
	if utf8.RuneCountInString(clean) <= textCellRunes {
		return clean
	}
	return string([]rune(clean)[:textCellRunes]) + "..."
}

// cell is value on one line with every terminal control removed, or "-" when nothing is left.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}
