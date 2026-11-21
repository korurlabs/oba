package session

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// refreshMargin renews a stored session's token this long before it expires, so that a call
// never leaves with a token that expires on the way.
const refreshMargin = 30 * time.Second

// Bearer is the sign-in of an API client other than dojo/client, such as the Kaya client, whose
// calls carry a context deadline and cannot retry a 401 the way dojo/client does: the API URL,
// the current organization and a token source.
type Bearer struct {
	APIURL string
	OrgID  string
	// Token returns the access token of the next call. A stored session is refreshed, and the
	// new tokens stored, shortly before it expires; OBA_TOKEN is sent as it is. It is safe for
	// concurrent use.
	Token func(ctx context.Context) (string, error)
}

// OrganizationBearer returns the bearer of the current organization, signed with OBA_TOKEN when
// it is set, else with the stored session.
func OrganizationBearer() (Bearer, error) {
	s, err := load()
	if err != nil {
		return Bearer{}, err
	}
	orgID, err := s.organizationID()
	if err != nil {
		return Bearer{}, err
	}
	if orgID == "" {
		return Bearer{}, ErrNoOrganization
	}
	if s.envToken {
		token := s.creds.AccessToken
		return Bearer{APIURL: s.creds.APIURL, OrgID: orgID, Token: func(context.Context) (string, error) { return token, nil }}, nil
	}
	endpoints := config.Resolve(config.Endpoints{APIURL: s.creds.APIURL, Issuer: s.creds.IssuerURL, ClientID: s.creds.ClientID})
	src := &refreshingToken{creds: s.creds, endpoints: endpoints, now: time.Now}
	return Bearer{APIURL: endpoints.APIURL, OrgID: orgID, Token: src.token}, nil
}

// refreshingToken hands out the stored session's access token and refreshes it once, for every
// caller, when it is about to expire.
type refreshingToken struct {
	mu        sync.Mutex
	creds     auth.Credentials
	endpoints config.Endpoints
	now       func() time.Time
}

func (r *refreshingToken) token(context.Context) (string, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	// A session stored without an expiry is sent as it is; the API answers 401 once it lapsed.
	if r.creds.ExpiresAt.IsZero() || r.now().Add(refreshMargin).Before(r.creds.ExpiresAt) {
		return r.creds.AccessToken, nil
	}
	if r.creds.RefreshToken == "" {
		return "", errors.New("your session has expired. Run 'oba login'")
	}
	resp, err := auth.RefreshTokenDirect(r.endpoints.Issuer, r.endpoints.ClientID, r.creds.RefreshToken)
	if err != nil {
		return "", fmt.Errorf("your session has expired and could not be refreshed (%w). Run 'oba login'", err)
	}
	r.creds.AccessToken = resp.AccessToken
	if resp.RefreshToken != "" {
		r.creds.RefreshToken = resp.RefreshToken
	}
	r.creds.ExpiresAt = r.now().Add(time.Duration(resp.ExpiresIn) * time.Second)
	if err := auth.NewTokenStore().Save(r.creds); err != nil {
		return "", fmt.Errorf("refreshed the session but could not store it: %w", err)
	}
	return r.creds.AccessToken, nil
}
