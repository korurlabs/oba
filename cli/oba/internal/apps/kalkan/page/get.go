package page

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Get reads the page at path with every translation. The id comes from the tree, so a path the
// caller cannot see is a 404 APIError.
func Get(c *client.Client, path string) (client.KalkanPageDetail, error) {
	item, err := Find(c, path)
	if err != nil {
		return client.KalkanPageDetail{}, err
	}
	return c.GetKalkanPage(item.ID)
}
