package client

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
)

// errNoOrganization refuses an organization-scoped call on a client without an organization:
// the member paths carry the organization id, and an empty one would address another route.
var errNoOrganization = errors.New("this call needs a client in an organization")

// Member is one member of an organization, a person who accepted an invitation, as
// GET /organizations/{organizationId}/members lists it (OrganizationMemberResponse). ID is the
// membership, UserID the person; the member calls address a member by UserID. FirstName and
// LastName come from the user's profile and are nil when unset.
type Member struct {
	ID        string  `json:"id"`
	UserID    string  `json:"userId"`
	Email     string  `json:"email"`
	FirstName *string `json:"firstName"`
	LastName  *string `json:"lastName"`
	Role      string  `json:"role"`
	Status    string  `json:"status"`
}

// memberRoleRequest is the OrganizationMemberRoleUpdateRequest body.
type memberRoleRequest struct {
	Role string `json:"role"`
}

// ListMembers reads every member of the client's organization. The API answers with one plain
// list, not pages.
func (c *Client) ListMembers() ([]Member, error) {
	path, err := c.membersPath()
	if err != nil {
		return nil, err
	}
	body, err := c.Get(path)
	if err != nil {
		return nil, err
	}
	var resp APIResponse[[]Member]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to read the members: %w", err)
	}
	return resp.Data, nil
}

// ChangeMemberRole gives the member with userID the role and returns the updated member. The
// API allows it to organization admins and owners, never assigns OWNER and refuses a change of
// the caller's own role.
func (c *Client) ChangeMemberRole(userID, role string) (Member, error) {
	path, err := c.memberPath(userID)
	if err != nil {
		return Member{}, err
	}
	body, err := c.Patch(path+"/role", memberRoleRequest{Role: role})
	if err != nil {
		return Member{}, err
	}
	var resp APIResponse[Member]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Member{}, fmt.Errorf("the role was changed, but the response could not be read: %w", err)
	}
	return resp.Data, nil
}

// RemoveMember removes the member with userID from the client's organization; the API allows it
// to organization admins and owners, and an admin never removes an owner.
func (c *Client) RemoveMember(userID string) error {
	path, err := c.memberPath(userID)
	if err != nil {
		return err
	}
	_, err = c.Delete(path)
	return err
}

func (c *Client) membersPath() (string, error) {
	if c.OrgID == "" {
		return "", errNoOrganization
	}
	return "/organizations/" + url.PathEscape(c.OrgID) + "/members", nil
}

func (c *Client) memberPath(userID string) (string, error) {
	path, err := c.membersPath()
	if err != nil {
		return "", err
	}
	return path + "/" + url.PathEscape(userID), nil
}
