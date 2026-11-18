package session

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// signIn is what the calls of one command are signed with: the token of OBA_TOKEN, or the
// stored session.
type signIn struct {
	creds auth.Credentials
	// envToken is set when the token came from OBA_TOKEN. Such a token has no refresh token
	// and no default organization stored with it.
	envToken bool
}

// EnvToken returns the token OBA_TOKEN holds, empty when it is not set.
func EnvToken() string {
	return strings.TrimSpace(os.Getenv(config.EnvToken))
}

// load returns OBA_TOKEN when it is set, without reading the stored session, else the stored
// session or auth.ErrNotLoggedIn.
func load() (signIn, error) {
	if token := EnvToken(); token != "" {
		endpoints := config.Resolve(config.Endpoints{})
		return signIn{
			creds: auth.Credentials{
				AccessToken: token,
				APIURL:      endpoints.APIURL,
				IssuerURL:   endpoints.Issuer,
				ClientID:    endpoints.ClientID,
			},
			envToken: true,
		}, nil
	}
	creds, err := auth.LoadSession()
	if err != nil {
		return signIn{}, err
	}
	return signIn{creds: creds}, nil
}

// client returns a client in the organization orgID, none when it is empty. A token of
// OBA_TOKEN is sent as it is and never refreshed; a stored session refreshes on a 401.
func (s signIn) client(orgID string) *client.Client {
	if s.envToken {
		return client.NewWithToken(s.creds.APIURL, orgID, s.creds.AccessToken)
	}
	return client.NewForSession(s.creds, orgID)
}

// user reads GET /users/me through c. The call sends no organization header: /users/me is
// exempt from it, and it must work before any organization is chosen.
func user(c *client.Client) (client.User, error) {
	body, err := c.Get("/users/me")
	if err != nil {
		return client.User{}, fmt.Errorf("failed to read your organizations: %w", err)
	}
	var resp client.APIResponse[client.User]
	if err := json.Unmarshal(body, &resp); err != nil {
		return client.User{}, fmt.Errorf("failed to parse the user profile: %w", err)
	}
	return resp.Data, nil
}
