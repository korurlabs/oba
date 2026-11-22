package documenttemplate

import (
	"context"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// Upload renders the .html file to PDF and replaces the HTML and the PDF of template id with
// them. The file is read and rendered before the call. Documents already made from the template
// keep their own files.
func Upload(ctx context.Context, c *client.Client, id, file string) (client.DocumentTemplate, error) {
	files, err := docfile.Load(ctx, file, "a document template")
	if err != nil {
		return client.DocumentTemplate{}, err
	}
	return c.ReplaceDocumentTemplateFiles(id, files)
}
