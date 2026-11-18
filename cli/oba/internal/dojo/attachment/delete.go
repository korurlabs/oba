package attachment

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Delete deletes attachment a, found with Find.
func Delete(c *client.Client, a client.Attachment) error {
	return c.DeleteAttachment(a.ID)
}
