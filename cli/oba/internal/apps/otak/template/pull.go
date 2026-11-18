package template

import (
	"github.com/korurlabs/oba-cli/internal/apps/otak/docfile"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Pull writes a template into dir as <name>.html and <name>.pdf, downloaded from their signed
// URLs. It returns the paths written.
func Pull(c *client.Client, templateID, dir string, overwrite bool) ([]string, error) {
	t, err := c.GetTemplate(templateID)
	if err != nil {
		return nil, err
	}
	stored := docfile.Stored{Kind: kind, ID: t.ID, HTMLURL: t.HTMLURL, PDFURL: t.PDFURL}
	files, err := stored.Files()
	if err != nil {
		return nil, err
	}
	return docfile.WriteFiles(dir, t.Name, t.ID, files, overwrite)
}
