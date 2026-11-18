package docfile

import (
	"context"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/otak/pdf"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Load reads the .html file of a document template of docType and renders it to PDF. It
// checks the extension first, so that everything that can fail locally fails before the API is
// called.
func Load(ctx context.Context, path, docType string) (client.DocumentFiles, error) {
	if err := CheckDocumentFile(path, docType); err != nil {
		return client.DocumentFiles{}, err
	}
	rendered, err := pdf.RenderFile(ctx, path)
	if err != nil {
		return client.DocumentFiles{}, err
	}
	return client.DocumentFiles{BaseName: BaseName(path), HTML: rendered.HTML, PDF: rendered.PDF}, nil
}

// Stored is a template as the API returned it, for a pull.
type Stored struct {
	Kind    string // "template", for messages
	ID      string
	HTMLURL *string
	PDFURL  *string
}

// Files returns the files a pull writes: the HTML and the PDF downloaded from their signed URLs.
func (s Stored) Files() ([]File, error) {
	if s.HTMLURL == nil || s.PDFURL == nil {
		return nil, fmt.Errorf("%s %s has no files yet; upload its HTML with 'push' first", s.Kind, s.ID)
	}
	html, err := client.Download(*s.HTMLURL)
	if err != nil {
		return nil, fmt.Errorf("failed to download the HTML of %s %s: %w", s.Kind, s.ID, err)
	}
	pdfData, err := client.Download(*s.PDFURL)
	if err != nil {
		return nil, fmt.Errorf("failed to download the PDF of %s %s: %w", s.Kind, s.ID, err)
	}
	return []File{{Ext: ExtHTML, Data: html}, {Ext: ExtPDF, Data: pdfData}}, nil
}
