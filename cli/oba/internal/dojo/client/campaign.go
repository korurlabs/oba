package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// CampaignsPath is the collection of the organization's campaigns.
const CampaignsPath = "/otak/campaigns"

// Campaign is one Otak campaign, the umbrella of related contents, as GET /otak/campaigns lists
// it (CampaignListResponse) and GET /otak/campaigns/{id} returns it (CampaignDetailResponse).
// StartDate and EndDate are date-only values (YYYY-MM-DD), nil when not set. ContentCount is the
// number of its contents; the contents themselves come from the nested contents list. Values
// keeps every property as the API sent it, so that an update writes back the unchanged fields
// byte for byte, since the API replaces them all.
type Campaign struct {
	ID           string                     `json:"id"`
	Name         string                     `json:"name"`
	Description  *string                    `json:"description"`
	StartDate    *string                    `json:"startDate"`
	EndDate      *string                    `json:"endDate"`
	Owner        *CampaignOwner             `json:"owner"`
	ContentCount int                        `json:"contentCount"`
	Values       map[string]json.RawMessage `json:"-"`
}

// CampaignOwner is the owner of a campaign; the update request takes its id as ownerUserId.
type CampaignOwner struct {
	ID   string `json:"id"`
	Name string `json:"name"`
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

// ListCampaigns reads every campaign of the organization that matches query (search, eventId) in
// one call: the endpoint is not paged and returns every match.
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

// GetCampaign reads every field of campaign id.
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

// UpdateCampaign replaces every field of campaign id with a CampaignUpdateRequest body and
// returns the result. Its contents are not part of the body and stay as they are.
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

// DeleteCampaign deletes campaign id with its contents and their images; the API allows it to
// organization admins and owners only.
func (c *Client) DeleteCampaign(id string) error {
	_, err := c.Delete(campaignPath(id))
	return err
}

func campaignPath(id string) string {
	return CampaignsPath + "/" + url.PathEscape(id)
}
