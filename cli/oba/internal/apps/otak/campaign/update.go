package campaign

import (
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// fieldOwnerUserID is the update field the detail response carries as an owner object instead.
const fieldOwnerUserID = "ownerUserId"

// Update changes only the given keys of campaign id: it reads the campaign, lays the given keys
// over its current values, null included, and sends every request field with PUT, since the API
// replaces them all. The contents are not part of the campaign body and stay as they are.
func Update(c *client.Client, id string, given map[string]json.RawMessage) (client.Campaign, error) {
	fields, err := LoadFields(c.BaseURL, UpdateFields)
	if err != nil {
		return client.Campaign{}, err
	}
	if err := apidocs.CheckKeys(given, fields); err != nil {
		return client.Campaign{}, err
	}
	current, err := c.GetCampaign(id)
	if err != nil {
		return client.Campaign{}, err
	}
	body, err := mergeUpdate(current, given, fields)
	if err != nil {
		return client.Campaign{}, err
	}
	return c.UpdateCampaign(id, body)
}

// mergeUpdate builds the PUT body: every request field with its current value and the given
// keys laid over it. ownerUserId is the id of the current owner. A field the campaign as read
// does not carry, and that was not given, is refused: the PUT would clear it without the user
// asking.
func mergeUpdate(current client.Campaign, given map[string]json.RawMessage, fields []apidocs.Field) (map[string]json.RawMessage, error) {
	body := make(map[string]json.RawMessage, len(fields))
	for _, field := range fields {
		if value, ok := given[field.Name]; ok {
			body[field.Name] = value
			continue
		}
		if field.Name == fieldOwnerUserID {
			value, err := ownerID(current.Owner)
			if err != nil {
				return nil, fmt.Errorf("cannot keep field %q: %w", field.Name, err)
			}
			body[field.Name] = value
			continue
		}
		value, ok := current.Values[field.Name]
		if !ok {
			return nil, fmt.Errorf("cannot keep field %q: the campaign as read has no such field; give its value", field.Name)
		}
		body[field.Name] = value
	}
	return body, nil
}

func ownerID(owner *client.CampaignOwner) (json.RawMessage, error) {
	if owner == nil || owner.ID == "" {
		return json.RawMessage("null"), nil
	}
	return json.Marshal(owner.ID)
}
