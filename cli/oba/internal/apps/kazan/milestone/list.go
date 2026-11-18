package milestone

import (
	"fmt"
	"io"
	"net/url"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads the milestones of board boardID: of status OPEN or CLOSED when status is given, and
// with related those of its parent, children and siblings too.
func List(c *client.Client, boardID, status string, related bool) ([]client.Milestone, error) {
	query := url.Values{}
	if s := strings.ToUpper(strings.TrimSpace(status)); s != "" {
		query.Set("status", s)
	}
	if related {
		query.Set("includeRelatedBoards", "true")
	}
	return c.ListMilestones(boardID, query)
}

// PrintList writes the milestones as a table, with a BOARD column when withBoard, or "No
// milestones found." when there are none.
func PrintList(out io.Writer, milestones []client.Milestone, withBoard bool) error {
	if len(milestones) == 0 {
		_, _ = fmt.Fprintln(out, "No milestones found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	header := "ID\tNAME\tSTATUS\tSTART\tTARGET\tPROGRESS\tHEALTH"
	if withBoard {
		header += "\tBOARD"
	}
	_, _ = fmt.Fprintln(w, header)
	for _, m := range milestones {
		start := "-"
		if m.StartDate != nil {
			start = *m.StartDate
		}
		line := fmt.Sprintf("%s\t%s\t%s\t%s\t%s\t%d/%d\t%s",
			m.ID, guardrail.OneLine(m.Name), m.Status, start, m.TargetDate, m.DoneCount, m.TotalCount, m.Health)
		if withBoard {
			line += "\t" + BoardLabel(m)
		}
		_, _ = fmt.Fprintln(w, line)
	}
	return w.Flush()
}
