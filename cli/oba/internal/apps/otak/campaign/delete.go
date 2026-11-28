package campaign

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes campaign id with its contents and their images; the outreaches and relations
// linked to it stay and lose the link.
func Delete(c *client.Client, id string) error {
	return c.DeleteCampaign(id)
}
