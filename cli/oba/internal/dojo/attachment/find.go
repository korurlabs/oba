package attachment

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Find returns attachment attachmentID of the parent entityType entityID, and false when the
// parent has no such attachment. The delete endpoint takes the attachment id alone, so this check
// keeps a mistyped id from deleting a file of another parent. Ids are compared case-insensitively,
// as a UUID may be typed in either case. The caller words the not-found error for its parent, and
// passes an unrecorded client when the list read must stay out of the --json output.
func Find(c *client.Client, entityType, entityID, attachmentID string) (client.Attachment, bool, error) {
	attachments, err := List(c, entityType, entityID)
	if err != nil {
		return client.Attachment{}, false, err
	}
	for _, a := range attachments {
		if strings.EqualFold(a.ID, attachmentID) {
			return a, true, nil
		}
	}
	return client.Attachment{}, false, nil
}
