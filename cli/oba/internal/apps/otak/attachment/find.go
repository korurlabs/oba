package attachment

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Find returns the attachment attachmentID of the target, so that a mistyped id never deletes an
// attachment of another mail or relation; see attachment.Find.
func Find(c *client.Client, t Target, attachmentID string) (client.Attachment, error) {
	found, ok, err := attachment.Find(c, t.EntityType, t.EntityID, attachmentID)
	if err != nil {
		return client.Attachment{}, err
	}
	if !ok {
		return client.Attachment{}, fmt.Errorf("attachment %s is not an attachment of %s; 'oba otak attachment list' lists the ids",
			attachmentID, t.Label)
	}
	return found, nil
}
