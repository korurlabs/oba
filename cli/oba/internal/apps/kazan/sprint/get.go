package sprint

import (
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Get reads the sprint of board boardID that name names, with its goal and linked boards.
func Get(c *client.Client, boardID, name string) (client.Sprint, error) {
	found, err := Find(c, boardID, name)
	if err != nil {
		return client.Sprint{}, err
	}
	return c.GetSprint(boardID, found.ID)
}

// PrintDetails writes one sprint as labelled lines, the creation time in the viewer's zone. Goal, boards and dates are shown only when the sprint has them.
func PrintDetails(out io.Writer, s client.Sprint) {
	line := func(label, value string) {
		_, _ = fmt.Fprintf(out, "%-12s %s\n", label, value)
	}
	line("Name:", guardrail.OneLine(s.Name))
	line("ID:", s.ID)
	line("Status:", s.Status)
	if s.Goal != nil {
		line("Goal:", guardrail.OneLine(*s.Goal))
	}
	if len(s.Boards) > 0 {
		names := make([]string, len(s.Boards))
		for i, b := range s.Boards {
			names[i] = guardrail.OneLine(b.Name)
		}
		line("Boards:", strings.Join(names, ", "))
	}
	if s.StartDate != nil {
		line("Start Date:", guardrail.OneLine(*s.StartDate))
	}
	if s.EndDate != nil {
		line("End Date:", guardrail.OneLine(*s.EndDate))
	}
	line("Created:", dates.FormatInstant(s.CreatedAt))
}
