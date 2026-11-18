package icp

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes ICP id; the relations linked to it stay and lose the link.
func Delete(c *client.Client, id string) error {
	return c.DeleteIcp(id)
}
