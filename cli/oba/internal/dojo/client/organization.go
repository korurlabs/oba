package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// GetOrganization reads the profile of organization id. The API allows it to organization
// admins and owners only.
func (c *Client) GetOrganization(id string) (OrganizationProfile, error) {
	body, err := c.Get("/organizations/" + url.PathEscape(id))
	if err != nil {
		return OrganizationProfile{}, err
	}
	var resp APIResponse[OrganizationProfile]
	if err := json.Unmarshal(body, &resp); err != nil {
		return OrganizationProfile{}, fmt.Errorf("failed to read the organization profile: %w", err)
	}
	return resp.Data, nil
}

// CreateOrganization sends an OrganizationCreateRequest body and returns the new organization,
// whose owner the caller becomes. POST /organizations is exempt from the organization header,
// so the client may have none.
func (c *Client) CreateOrganization(body any) (OrganizationProfile, error) {
	resp, err := c.Post("/organizations", body)
	if err != nil {
		return OrganizationProfile{}, err
	}
	var parsed APIResponse[OrganizationProfile]
	if err := json.Unmarshal(resp, &parsed); err != nil {
		return OrganizationProfile{}, fmt.Errorf("the organization was created, but the response could not be read: %w", err)
	}
	return parsed.Data, nil
}

// UpdateOrganization sends an OrganizationUpdateRequest body to PUT /organizations/{id} and
// returns the organization as stored. The API replaces every field, a null optional field
// clears it, and it refuses an id other than the one in the organization header.
func (c *Client) UpdateOrganization(id string, body any) (OrganizationProfile, error) {
	resp, err := c.Put("/organizations/"+url.PathEscape(id), body)
	if err != nil {
		return OrganizationProfile{}, err
	}
	var parsed APIResponse[OrganizationProfile]
	if err := json.Unmarshal(resp, &parsed); err != nil {
		return OrganizationProfile{}, fmt.Errorf("the organization was updated, but the response could not be read: %w", err)
	}
	return parsed.Data, nil
}
