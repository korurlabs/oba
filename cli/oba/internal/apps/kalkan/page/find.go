package page

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Find looks the page at path up in the tree of pages the caller can see; a path the tree does
// not hold is a 404 APIError.
func Find(c *client.Client, path string) (client.KalkanPageListItem, error) {
	return c.FindKalkanPage(path)
}
