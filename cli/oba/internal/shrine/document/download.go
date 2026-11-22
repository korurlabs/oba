package document

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// Download writes the HTML of document id to output, or to <title>.html in the current
// directory, downloaded from its signed URL. An existing file is replaced only with overwrite.
// It returns the path written.
func Download(c *client.Client, id, output string, overwrite bool) (string, error) {
	d, err := c.GetDocument(id)
	if err != nil {
		return "", err
	}
	html, err := docfile.DownloadHTML(d.HTMLURL, "document "+d.ID, "oba document upload "+d.ID+" <file.html>")
	if err != nil {
		return "", err
	}
	path := docfile.HTMLPath(output, d.Title, d.ID)
	if err := docfile.WriteFile(path, html, overwrite); err != nil {
		return "", err
	}
	return path, nil
}
