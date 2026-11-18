package relation

import (
	"net/url"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes relation id.
func Delete(c *client.Client, id string) error {
	_, err := c.Delete(relationsPath + "/" + url.PathEscape(id))
	return err
}
