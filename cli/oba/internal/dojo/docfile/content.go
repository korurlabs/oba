package docfile

import (
	"context"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/pdf"
)

// Load reads the .html file of a document or document template and renders it to PDF. what
// names the target in the extension message ("an NDA", "a document"). It checks the extension
// first, so that everything that can fail locally fails before the API is called.
func Load(ctx context.Context, path, what string) (client.DocumentFiles, error) {
	if err := CheckDocumentFile(path, what); err != nil {
		return client.DocumentFiles{}, err
	}
	rendered, err := pdf.RenderFile(ctx, path)
	if err != nil {
		return client.DocumentFiles{}, err
	}
	return client.DocumentFiles{BaseName: BaseName(path), HTML: rendered.HTML, PDF: rendered.PDF}, nil
}
