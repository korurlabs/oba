package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// InvitationsPath is the collection of the organization's invitations; the organization is the
// X-Organization-ID header, not part of the path.
const InvitationsPath = "/invitations"

// Invitation is one invitation to join an organization (InvitationResponse). CreatedAt and
// ExpiresAt are RFC 3339 instants in UTC; ExpiresAt is nil when the API sends none.
type Invitation struct {
	ID             string  `json:"id"`
	Email          string  `json:"email"`
	Role           string  `json:"role"`
	Status         string  `json:"status"`
	InvitedByEmail string  `json:"invitedByEmail"`
	CreatedAt      string  `json:"createdAt"`
	ExpiresAt      *string `json:"expiresAt"`
}

// invitationCreateRequest is the InvitationCreateRequest body.
type invitationCreateRequest struct {
	Email string `json:"email"`
	Role  string `json:"role"`
}

// CreateInvitation invites email with role to the client's organization and returns the
// invitation. The person joins when they accept it in the account app, whether or not they
// already have an Oba account. The API allows it to organization admins and owners.
func (c *Client) CreateInvitation(email, role string) (Invitation, error) {
	body, err := c.Post(InvitationsPath, invitationCreateRequest{Email: email, Role: role})
	if err != nil {
		return Invitation{}, err
	}
	var resp APIResponse[Invitation]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Invitation{}, fmt.Errorf("the invitation was created, but the response could not be read: %w", err)
	}
	return resp.Data, nil
}

// ListInvitations reads the pending invitations of the client's organization. The API answers
// with one plain list, not pages.
func (c *Client) ListInvitations() ([]Invitation, error) {
	body, err := c.Get(InvitationsPath)
	if err != nil {
		return nil, err
	}
	var resp APIResponse[[]Invitation]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to read the invitations: %w", err)
	}
	return resp.Data, nil
}

// RevokeInvitation revokes the pending invitation id of the client's organization.
func (c *Client) RevokeInvitation(id string) error {
	_, err := c.Delete(InvitationsPath + "/" + url.PathEscape(id))
	return err
}
