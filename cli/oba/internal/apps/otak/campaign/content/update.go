package content

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Update changes only the given keys of content contentID: it reads the contents of the
// campaign, lays the given keys over the content's current values, null included, and sends
// every request field with PUT, since the API replaces them all. The PUT goes to the id as the
// API spells it, since the given one is matched case ignored.
func Update(c *client.Client, campaignID, contentID string, given map[string]json.RawMessage) (client.CampaignContent, error) {
	fields, err := LoadFields(c.BaseURL)
	if err != nil {
		return client.CampaignContent{}, err
	}
	if err := apidocs.CheckKeys(given, fields); err != nil {
		return client.CampaignContent{}, err
	}
	contents, err := c.ListCampaignContents(campaignID)
	if err != nil {
		return client.CampaignContent{}, err
	}
	current, err := find(contents, campaignID, contentID)
	if err != nil {
		return client.CampaignContent{}, err
	}
	body, err := mergeUpdate(current, given, fields)
	if err != nil {
		return client.CampaignContent{}, err
	}
	return c.UpdateCampaignContent(campaignID, current.ID, body)
}

// find picks contentID from the campaign's contents. An absent content is a not-found error, so
// that the exit status is the one the API gives for an unknown content.
func find(contents []client.CampaignContent, campaignID, contentID string) (client.CampaignContent, error) {
	for _, cc := range contents {
		if strings.EqualFold(cc.ID, contentID) {
			return cc, nil
		}
	}
	return client.CampaignContent{}, &client.APIError{
		Status:  http.StatusNotFound,
		Message: fmt.Sprintf("content %s not found on campaign %s", contentID, campaignID),
	}
}

// mergeUpdate builds the PUT body: every request field with its current value and the given
// keys laid over it. A field the content as read does not carry, and that was not given, is
// refused: the PUT would clear it without the user asking.
func mergeUpdate(current client.CampaignContent, given map[string]json.RawMessage, fields []apidocs.Field) (map[string]json.RawMessage, error) {
	body := make(map[string]json.RawMessage, len(fields))
	for _, field := range fields {
		if value, ok := given[field.Name]; ok {
			body[field.Name] = value
			continue
		}
		value, ok := current.Values[field.Name]
		if !ok {
			return nil, fmt.Errorf("cannot keep field %q: the content as read has no such field; give its value", field.Name)
		}
		body[field.Name] = value
	}
	return body, nil
}
