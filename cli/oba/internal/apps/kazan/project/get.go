package project

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Get reads project id of board boardID.
func Get(c *client.Client, boardID, id string) (client.Project, error) {
	id, err := checkID(id)
	if err != nil {
		return client.Project{}, err
	}
	return c.GetProject(boardID, id)
}

// PrintDetails writes one project as labelled lines; the color line only when it has one.
func PrintDetails(out io.Writer, p client.Project) {
	_, _ = fmt.Fprintf(out, "Name:  %s\n", guardrail.OneLine(p.Name))
	_, _ = fmt.Fprintf(out, "ID:    %s\n", guardrail.OneLine(p.ID))
	if p.Color != nil {
		_, _ = fmt.Fprintf(out, "Color: %s\n", guardrail.OneLine(*p.Color))
	}
}
