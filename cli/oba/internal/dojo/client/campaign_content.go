package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// StorageEntityTypeCampaignContent is the StorageEntityType of a campaign content's images.
const StorageEntityTypeCampaignContent = "CAMPAIGN_CONTENT"

// CampaignContent is one content of a campaign, one thing actually shared, such as one LinkedIn
// post: one channel, one language, one text, published by one legal entity, as the contents list
// and a create or update return it (CampaignContentResponse). PlannedAt and PublishedAt are RFC
// 3339 instants, nil when not set. Values keeps every property as the API sent it, so that an
// update writes back the unchanged fields byte for byte, since the API replaces them all.
type CampaignContent struct {
	ID                   string                     `json:"id"`
	CampaignID           string                     `json:"campaignId"`
	LegalEntityID        string                     `json:"legalEntityId"`
	LegalEntityShortName string                     `json:"legalEntityShortName"`
	Channel              string                     `json:"channel"`
	Language             string                     `json:"language"`
	Text                 string                     `json:"text"`
	Status               string                     `json:"status"`
	PlannedAt            *string                    `json:"plannedAt"`
	PublishedAt          *string                    `json:"publishedAt"`
	PostURL              *string                    `json:"postUrl"`
	Values               map[string]json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (cc *CampaignContent) UnmarshalJSON(data []byte) error {
	type plain CampaignContent
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	if err := json.Unmarshal(data, &typed.Values); err != nil {
		return err
	}
	*cc = CampaignContent(typed)
	return nil
}

// ListCampaignContents reads every content of campaign campaignID in the API's agenda order:
// soonest planned first, unplanned last, then last changed first. The endpoint is not paged.
func (c *Client) ListCampaignContents(campaignID string) ([]CampaignContent, error) {
	body, err := c.Get(campaignContentsPath(campaignID))
	if err != nil {
		return nil, err
	}
	return parseData[[]CampaignContent](body, "the contents of campaign "+campaignID)
}

// CreateCampaignContent sends a CampaignContentRequest body and returns the created content.
func (c *Client) CreateCampaignContent(campaignID string, body any) (CampaignContent, error) {
	resp, err := c.Post(campaignContentsPath(campaignID), body)
	if err != nil {
		return CampaignContent{}, err
	}
	content, err := parseData[CampaignContent](resp, "the created content")
	if err != nil {
		return CampaignContent{}, fmt.Errorf("the content was created, but the response could not be read: %w", err)
	}
	return content, nil
}

// UpdateCampaignContent replaces every field of content contentID with a CampaignContentRequest
// body and returns the result.
func (c *Client) UpdateCampaignContent(campaignID, contentID string, body any) (CampaignContent, error) {
	resp, err := c.Put(campaignContentPath(campaignID, contentID), body)
	if err != nil {
		return CampaignContent{}, err
	}
	content, err := parseData[CampaignContent](resp, "content "+contentID)
	if err != nil {
		return CampaignContent{}, fmt.Errorf("content %s was updated, but the response could not be read: %w", contentID, err)
	}
	return content, nil
}

// DeleteCampaignContent deletes content contentID of campaign campaignID with its images.
func (c *Client) DeleteCampaignContent(campaignID, contentID string) error {
	_, err := c.Delete(campaignContentPath(campaignID, contentID))
	return err
}

func campaignContentsPath(campaignID string) string {
	return campaignPath(campaignID) + "/contents"
}

func campaignContentPath(campaignID, contentID string) string {
	return campaignContentsPath(campaignID) + "/" + url.PathEscape(contentID)
}
