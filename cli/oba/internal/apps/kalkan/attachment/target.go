package attachment

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Target is the page whose attachments a command reads or changes: its storage entity type and
// id, and a label that names it in messages, such as "page engineering/plans".
type Target struct {
	EntityType string
	EntityID   string
	Label      string
}

// ResolveTarget finds the page at the normalized pagePath. A path the caller cannot see is a 404
// APIError, so that it exits as a denial does.
func ResolveTarget(c *client.Client, pagePath string) (Target, error) {
	page, err := c.FindKalkanPage(pagePath)
	if err != nil {
		return Target{}, err
	}
	return Target{EntityType: client.StorageEntityTypeKalkanPage, EntityID: page.ID, Label: "page " + page.Path}, nil
}
