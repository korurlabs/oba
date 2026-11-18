package comment

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads the comments of ticket t.
func List(c *client.Client, t ticket.Target) ([]client.Comment, error) {
	return c.ListComments(t.BoardID, t.ID)
}

// PrintList writes the comments as one table: id, author, when it was written and the start of
// its content.
func PrintList(out io.Writer, comments []client.Comment) error {
	if len(comments) == 0 {
		_, _ = fmt.Fprintln(out, "No comments found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "ID\tAUTHOR\tDATE\tCONTENT")
	for _, cm := range comments {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", guardrail.OneLine(cm.ID),
			author(cm.AuthorFirstName, cm.AuthorLastName, cm.AuthorEmail), guardrail.OneLine(cm.CreatedAt), preview(cm.Content))
	}
	return w.Flush()
}
