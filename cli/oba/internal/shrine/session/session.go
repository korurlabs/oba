// Package session turns the sign-in into what the commands act with: the API URL, the
// signed-in user and a client in the current organization. The sign-in is OBA_TOKEN when it is
// set, else the stored session; the organization is the one --org names, else the chosen one.
package session

import (
	"errors"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// ErrNoOrganization is printed as it is, so it keeps the capital and the full stop.
var ErrNoOrganization = errors.New("No organization selected. Run 'oba org use <name|id>'.") //nolint:staticcheck // ST1005: shown to the user verbatim

// OrganizationClient returns a client in the current organization, signed with OBA_TOKEN when
// it is set, else with the stored session.
func OrganizationClient() (*client.Client, error) {
	s, err := load()
	if err != nil {
		return nil, err
	}
	orgID, err := s.organizationID()
	if err != nil {
		return nil, err
	}
	if orgID == "" {
		return nil, ErrNoOrganization
	}
	return s.client(orgID), nil
}

// UserClient returns a client that sends no organization header, for the organization-exempt
// calls such as creating an organization, which must work before the user belongs to any.
func UserClient() (*client.Client, error) {
	s, err := load()
	if err != nil {
		return nil, err
	}
	return s.client(""), nil
}

// CurrentUser reads GET /users/me. The credentials it returns carry the default organization,
// for OBA_TOKEN the one /users/me names.
func CurrentUser() (auth.Credentials, client.User, error) {
	s, err := load()
	if err != nil {
		return auth.Credentials{}, client.User{}, err
	}
	u, err := user(s.client(""))
	if err != nil {
		return auth.Credentials{}, client.User{}, err
	}
	if s.envToken && u.DefaultOrganizationID != nil {
		s.creds.DefaultOrgID = *u.DefaultOrganizationID
	}
	return s.creds, u, nil
}

// APIURL is the API a command reads without needing a session, such as help: the stored
// session's, else the environment or the default. Only the absence of a session falls back; an
// unreadable store is reported.
func APIURL() (string, error) {
	s, err := load()
	if errors.Is(err, auth.ErrNotLoggedIn) {
		return config.Resolve(config.Endpoints{}).APIURL, nil
	}
	if err != nil {
		return "", err
	}
	return config.Resolve(config.Endpoints{APIURL: s.creds.APIURL, Issuer: s.creds.IssuerURL, ClientID: s.creds.ClientID}).APIURL, nil
}

// OrganizationName returns the name of the organization with the given id among the signed-in
// user's memberships, for a confirmation question; the id itself when the memberships do not
// list it.
func OrganizationName(orgID string) (string, error) {
	_, user, err := CurrentUser()
	if err != nil {
		return "", err
	}
	for _, org := range user.Organizations {
		if org.ID == orgID {
			return org.Name, nil
		}
	}
	return orgID, nil
}
