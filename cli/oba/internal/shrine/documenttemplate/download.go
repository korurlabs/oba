package documenttemplate

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// Download writes the HTML of template id to output, or to <name>.html in the current directory,
// downloaded from its signed URL. An existing file is replaced only with overwrite. It returns
// the path written.
func Download(c *client.Client, id, output string, overwrite bool) (string, error) {
	t, err := c.GetDocumentTemplate(id)
	if err != nil {
		return "", err
	}
	html, err := docfile.DownloadHTML(t.HTMLURL, "document template "+t.ID,
		"oba document template upload "+t.ID+" <file.html>")
	if err != nil {
		return "", err
	}
	path := docfile.HTMLPath(output, t.Name, t.ID)
	if err := docfile.WriteFile(path, html, overwrite); err != nil {
		return "", err
	}
	return path, nil
}
