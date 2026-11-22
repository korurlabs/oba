package legalentity

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Delete deletes legal entity id.
func Delete(c *client.Client, id string) error {
	return c.DeleteLegalEntity(id)
}
