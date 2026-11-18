package ticket

import (
	"fmt"
	"net/http"

	"github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// FindAttachment returns attachment attachmentID of ticket ticketID, as attachment.Find does. The
// list read is a lookup and stays out of the --json output; an id that is not on the ticket is an
// API 404, so the command exits 2.
func FindAttachment(c *client.Client, ticketID, attachmentID string) (client.Attachment, error) {
	found, ok, err := attachment.Find(c.Unrecorded(), AttachmentEntityType, ticketID, attachmentID)
	if err != nil {
		return client.Attachment{}, err
	}
	if !ok {
		return client.Attachment{}, &client.APIError{
			Status:  http.StatusNotFound,
			Message: fmt.Sprintf("attachment %s is not attached to this ticket", guardrail.OneLine(attachmentID)),
		}
	}
	return found, nil
}
