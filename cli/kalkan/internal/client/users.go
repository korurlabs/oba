package client

import (
	"context"
	"net/http"
)

// UserMembership is an organization the user belongs to, as the platform lists it.
type UserMembership struct {
	OrganizationID string `json:"id"`
	Name           string `json:"name"`
	Role           string `json:"role"`
}

// UserMe is the signed-in platform user with the organizations it belongs to.
type UserMe struct {
	ID          string           `json:"id"`
	Email       string           `json:"email"`
	Memberships []UserMembership `json:"organizations"`
}

// ProvisionMe signs the user in to the platform: POST /users/me creates the user on the first
// call, links the Keycloak id, and answers the same user on every later one. Kalkan keeps no users
// or memberships of its own; the organizations come from the platform.
func (c *Client) ProvisionMe(ctx context.Context) (UserMe, error) {
	var out UserMe
	err := c.WithOrg("").doJSON(ctx, http.MethodPost, "/users/me", nil, &out)
	return out, err
}
