package attachment

import (
	"errors"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Target is the parent whose attachments a command reads or changes: its storage entity type and
// id, and a label that names it in messages, such as "outreach <id>".
type Target struct {
	EntityType string
	EntityID   string
	Label      string
}

// ResolveTarget turns --outreach, --relation or --campaign, exactly one of them, into the parent.
// An outreach is read to find the id of its own mail; a WHATSAPP or CALL outreach has no mail and
// is refused.
func ResolveTarget(c *client.Client, outreachID, relationID, campaignID string) (Target, error) {
	given := 0
	for _, id := range []string{outreachID, relationID, campaignID} {
		if id != "" {
			given++
		}
	}
	if given != 1 {
		return Target{}, errors.New("pass exactly one of --outreach, --relation and --campaign")
	}
	if relationID != "" {
		return Target{EntityType: client.StorageEntityTypeRelation, EntityID: relationID, Label: "relation " + relationID}, nil
	}
	if campaignID != "" {
		return Target{EntityType: client.StorageEntityTypeCampaign, EntityID: campaignID, Label: "campaign " + campaignID}, nil
	}
	o, err := c.GetOutreach(outreachID)
	if err != nil {
		return Target{}, err
	}
	if o.Mail == nil || o.Mail.ID == "" {
		return Target{}, fmt.Errorf("outreach %s is a %s outreach and has no mail to attach to", outreachID, orDash(o.Type))
	}
	return Target{EntityType: client.StorageEntityTypeMail, EntityID: o.Mail.ID, Label: "outreach " + outreachID}, nil
}
