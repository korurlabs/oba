package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// CampaignsPath is the collection of the organization's campaigns.
const CampaignsPath = "/otak/campaigns"

// StorageEntityTypeCampaign is the StorageEntityType of a campaign's images.
const StorageEntityTypeCampaign = "CAMPAIGN"

// Campaign is one Otak campaign, one post or mailing on one channel, as GET /otak/campaigns lists
// it (CampaignListResponse) and GET /otak/campaigns/{id} returns it (CampaignDetailResponse);
// PostURL and Contents are set by the detail call only. PlannedAt and PublishedAt are RFC 3339
// instants, nil when not set. Values keeps every property as the API sent it, so that an update
// writes back the unchanged fields byte for byte, since the API replaces them all.
type Campaign struct {
	ID          string                     `json:"id"`
	Name        string                     `json:"name"`
	Channel     string                     `json:"channel"`
	Status      string                     `json:"status"`
	PlannedAt   *string                    `json:"plannedAt"`
	PublishedAt *string                    `json:"publishedAt"`
	PostURL     *string                    `json:"postUrl"`
	Languages   []string                   `json:"languages"`
	Owner       *CampaignOwner             `json:"owner"`
	Contents    []CampaignContent          `json:"contents"`
	Values      map[string]json.RawMessage `json:"-"`
}

// CampaignOwner is the owner of a campaign; the update request takes its id as ownerUserId.
type CampaignOwner struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// CampaignContent is the text of a campaign in one language (NL, TR or EN), as the request takes
// it; the detail also sends each text's updatedAt, which a request does not carry.
type CampaignContent struct {
	Language string `json:"language"`
	Text     string `json:"text"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (c *Campaign) UnmarshalJSON(data []byte) error {
	type plain Campaign
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	if err := json.Unmarshal(data, &typed.Values); err != nil {
		return err
	}
	*c = Campaign(typed)
	return nil
}

// ListCampaigns reads every campaign of the organization that matches query (status, channel,
// search) in one call: the endpoint is not paged and returns every match.
func (c *Client) ListCampaigns(query url.Values) ([]Campaign, error) {
	path := CampaignsPath
	if len(query) > 0 {
		path += "?" + query.Encode()
	}
	body, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	return parseData[[]Campaign](body, "the campaigns")
}

// GetCampaign reads every field of campaign id, its texts included.
func (c *Client) GetCampaign(id string) (Campaign, error) {
	body, err := c.Get(campaignPath(id))
	if err != nil {
		return Campaign{}, err
	}
	return parseData[Campaign](body, "campaign "+id)
}

// CreateCampaign sends a CampaignCreateRequest body and returns the created campaign.
func (c *Client) CreateCampaign(body any) (Campaign, error) {
	resp, err := c.Post(CampaignsPath, body)
	if err != nil {
		return Campaign{}, err
	}
	campaign, err := parseData[Campaign](resp, "the created campaign")
	if err != nil {
		return Campaign{}, fmt.Errorf("the campaign was created, but the response could not be read: %w", err)
	}
	return campaign, nil
}

// UpdateCampaign replaces every field and the whole set of texts of campaign id with a
// CampaignUpdateRequest body and returns the result.
func (c *Client) UpdateCampaign(id string, body any) (Campaign, error) {
	resp, err := c.Put(campaignPath(id), body)
	if err != nil {
		return Campaign{}, err
	}
	campaign, err := parseData[Campaign](resp, "campaign "+id)
	if err != nil {
		return Campaign{}, fmt.Errorf("campaign %s was updated, but the response could not be read: %w", id, err)
	}
	return campaign, nil
}

// DeleteCampaign deletes campaign id with its texts and images; the API allows it to
// organization admins and owners only.
func (c *Client) DeleteCampaign(id string) error {
	_, err := c.Delete(campaignPath(id))
	return err
}

func campaignPath(id string) string {
	return CampaignsPath + "/" + url.PathEscape(id)
}
