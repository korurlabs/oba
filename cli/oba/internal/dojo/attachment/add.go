package attachment

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Add uploads files, read with ReadFiles, as attachments of the parent entityType entityID in one
// request, so that either all of them are stored or none.
func Add(c *client.Client, entityType, entityID string, files []client.AttachmentFile) ([]client.Attachment, error) {
	return c.AddAttachments(entityType, entityID, files)
}
