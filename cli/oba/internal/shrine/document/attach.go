package document

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ParseMailID checks the --mail value before any request.
func ParseMailID(value string) (string, error) {
	return flagID("--mail", value, "oba otak outreach create")
}

// AttachToMail stores a copy of the current PDF of document id on mail mailID, a mail of the
// same relation that is not sent yet.
func AttachToMail(c *client.Client, id, mailID string) (client.Attachment, error) {
	return c.AttachDocumentToMail(id, mailID)
}
