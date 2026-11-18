package attachment

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// List reads the attachments of the parent entityType entityID in upload order.
func List(c *client.Client, entityType, entityID string) ([]client.Attachment, error) {
	return c.ListAttachments(entityType, entityID)
}

// PrintList writes the attachments as one table: file name, size in bytes, media type, when it
// was added, and id.
func PrintList(out io.Writer, attachments []client.Attachment) error {
	if len(attachments) == 0 {
		_, _ = fmt.Fprintln(out, "No attachments.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tSIZE\tTYPE\tCREATED\tID")
	for _, a := range attachments {
		_, _ = fmt.Fprintf(w, "%s\t%d\t%s\t%s\t%s\n", orDash(a.FileName), a.FileSize, orDash(a.ContentType),
			orDash(a.CreatedAt), orDash(a.ID))
	}
	return w.Flush()
}
