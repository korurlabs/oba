package attachment

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// AddFromTemplate copies the PDF of document template templateID onto the target as a new
// attachment named after the template. A template without its PDF is refused with OTK_VAL_012.
func AddFromTemplate(c *client.Client, t Target, templateID string) (client.Attachment, error) {
	return c.CopyTemplateToAttachment(templateID, t.EntityType, t.EntityID)
}
