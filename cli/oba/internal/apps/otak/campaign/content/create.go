package content

import (
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Create checks the keys of obj against the request fields and creates a content of campaign
// campaignID. Without legalEntityID it sends body, the text obj was parsed from, as it is:
// encoding/json only compacts a RawMessage, so every value reaches the API as it was given. With
// legalEntityID, that id replaces any legalEntityId of obj and the object is sent re-encoded.
func Create(c *client.Client, campaignID string, fields []apidocs.Field, body json.RawMessage, obj map[string]json.RawMessage,
	legalEntityID string) (client.CampaignContent, error) {
	if err := apidocs.CheckKeys(obj, fields); err != nil {
		return client.CampaignContent{}, err
	}
	if legalEntityID == "" {
		return c.CreateCampaignContent(campaignID, body)
	}
	id, err := json.Marshal(legalEntityID)
	if err != nil {
		return client.CampaignContent{}, fmt.Errorf("failed to encode the legal entity id: %w", err)
	}
	obj[fieldLegalEntityID] = id
	return c.CreateCampaignContent(campaignID, obj)
}
