package document

import (
	"context"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// Upload renders the .html file to PDF and replaces the HTML and the PDF of document id with
// them. The file is read and rendered before the call. Copies already attached to mails keep
// their own PDF.
func Upload(ctx context.Context, c *client.Client, id, file string) (client.Document, error) {
	files, err := docfile.Load(ctx, file, "a document")
	if err != nil {
		return client.Document{}, err
	}
	return c.ReplaceDocumentFiles(id, files)
}
