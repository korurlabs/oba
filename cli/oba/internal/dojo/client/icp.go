package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// IcpsPath is the collection of the organization's ICPs.
const IcpsPath = "/otak/icps"

// ListIcps reads every ICP of the organization, in the API's order: by name.
func (c *Client) ListIcps() ([]Icp, error) {
	return ListPages[Icp](c, IcpsPath, url.Values{})
}

// GetIcp reads every field of ICP id.
func (c *Client) GetIcp(id string) (Icp, error) {
	body, err := c.Get(icpPath(id))
	if err != nil {
		return Icp{}, err
	}
	icp, err := parseIcp(body)
	if err != nil {
		return Icp{}, fmt.Errorf("failed to read ICP %s: %w", id, err)
	}
	return icp, nil
}

// CreateIcp sends an IcpCreateRequest body and returns the created ICP.
func (c *Client) CreateIcp(body any) (Icp, error) {
	resp, err := c.Post(IcpsPath, body)
	if err != nil {
		return Icp{}, err
	}
	icp, err := parseIcp(resp)
	if err != nil {
		return Icp{}, fmt.Errorf("the ICP was created, but the response could not be read: %w", err)
	}
	return icp, nil
}

// UpdateIcp replaces every field of ICP id with an IcpUpdateRequest body and returns the result.
func (c *Client) UpdateIcp(id string, body any) (Icp, error) {
	resp, err := c.Put(icpPath(id), body)
	if err != nil {
		return Icp{}, err
	}
	icp, err := parseIcp(resp)
	if err != nil {
		return Icp{}, fmt.Errorf("ICP %s was updated, but the response could not be read: %w", id, err)
	}
	return icp, nil
}

// DeleteIcp deletes ICP id; the API allows it to organization admins and owners only.
func (c *Client) DeleteIcp(id string) error {
	_, err := c.Delete(icpPath(id))
	return err
}

func icpPath(id string) string {
	return IcpsPath + "/" + url.PathEscape(id)
}

func parseIcp(body []byte) (Icp, error) {
	var resp APIResponse[Icp]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Icp{}, err
	}
	return resp.Data, nil
}
