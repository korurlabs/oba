package auth

import (
	"errors"
	"fmt"
	"time"
)

// Credentials is the one stored session: the tokens, and the API, issuer and client they were
// obtained for, so that later commands call the same place without signing in again.
type Credentials struct {
	AccessToken  string    `json:"access_token"`
	RefreshToken string    `json:"refresh_token"`
	ExpiresAt    time.Time `json:"expires_at"`
	APIURL       string    `json:"api_url"`
	IssuerURL    string    `json:"issuer_url"`
	ClientID     string    `json:"client_id"`
	UserID       string    `json:"user_id,omitempty"`
	DefaultOrgID string    `json:"default_org_id,omitempty"`
}

// TokenStore holds the single session of this host.
type TokenStore interface {
	Save(creds Credentials) error
	Load() (Credentials, error)
	Delete() error
}

// ErrNoCredentials is wrapped by Load of every backend when nothing has been saved, so callers
// detect "not signed in" with errors.Is whichever backend is active.
var ErrNoCredentials = errors.New("no credentials found")

// ErrNotLoggedIn is the error a command that needs the API returns without a session. Its text
// is printed to the user as it is, a full sentence with the command that fixes it.
var ErrNotLoggedIn = errors.New("You are not logged in. Run 'oba login'.") //nolint:staticcheck // ST1005: shown to the user verbatim, never wrapped

// LoadSession returns the stored session, or ErrNotLoggedIn when there is none. Any other
// failure (an unreachable secret service, an unreadable file) is returned as it is.
func LoadSession() (Credentials, error) {
	creds, err := NewTokenStore().Load()
	if err != nil {
		if errors.Is(err, ErrNoCredentials) {
			return Credentials{}, ErrNotLoggedIn
		}
		return Credentials{}, fmt.Errorf("could not load the stored session: %w", err)
	}
	return creds, nil
}
