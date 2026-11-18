package outreach

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Complete records a DRAFT WHATSAPP or CALL outreach as done. The API refuses a MAIL
// (OTK_VAL_014), an outreach already done (OTK_STA_011) and a contact without phone
// (OTK_VAL_015), so nothing is checked here.
func Complete(c *client.Client, outreachID string) (client.Outreach, error) {
	return c.CompleteOutreach(outreachID)
}
