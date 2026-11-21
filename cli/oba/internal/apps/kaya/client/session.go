package client

import "github.com/korurlabs/oba-cli/internal/shrine/session"

// ForOrganization returns a client for the user calls of the current organization, such as a
// seed, signed with the Oba sign-in (OBA_TOKEN, else the session of `oba login`) and acting in
// the organization --org names, else the chosen one.
func ForOrganization() (*Client, error) {
	bearer, err := session.OrganizationBearer()
	if err != nil {
		return nil, err
	}
	return New(bearer.APIURL, bearer.OrgID, bearer.Token), nil
}
