package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// ErrNotSignedIn means there is no usable sign-in: no credentials are stored on this machine, or
// the token endpoint refused to refresh them. Either way only kalkan login helps, so callers map
// it to the exit code for "not signed in" with errors.Is.
var ErrNotSignedIn = errors.New("not signed in; run kalkan login")

// Membership is one organization the signed-in user can act in.
type Membership struct {
	OrganizationID string `json:"organizationId"`
	Name           string `json:"name"`
	Role           string `json:"role"`
}

// Credentials are the stored sign-in: the tokens, where they came from, and the organization
// user calls act in.
type Credentials struct {
	AccessToken  string       `json:"accessToken"`
	RefreshToken string       `json:"refreshToken"`
	ExpiresAt    time.Time    `json:"expiresAt"`
	Issuer       string       `json:"issuer"`
	ClientID     string       `json:"clientId"`
	APIURL       string       `json:"apiUrl"`
	OrgID        string       `json:"orgId,omitempty"`
	Email        string       `json:"email,omitempty"`
	Memberships  []Membership `json:"memberships,omitempty"`
}

// Store keeps the credentials in one file only the user can read.
type Store struct {
	path string
}

// DefaultStore is the store under the user's config directory: kalkan/credentials.json.
func DefaultStore() (*Store, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return nil, fmt.Errorf("find the config directory: %w", err)
	}
	return NewStore(filepath.Join(dir, "kalkan", "credentials.json")), nil
}

// NewStore returns a store at path.
func NewStore(path string) *Store {
	return &Store{path: path}
}

// Path is the file the store writes.
func (s *Store) Path() string {
	return s.path
}

// Load reads the credentials; ErrNotSignedIn when there are none.
func (s *Store) Load() (Credentials, error) {
	data, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return Credentials{}, ErrNotSignedIn
	}
	if err != nil {
		return Credentials{}, fmt.Errorf("read %s: %w", s.path, err)
	}
	var creds Credentials
	if err := json.Unmarshal(data, &creds); err != nil {
		return Credentials{}, fmt.Errorf("parse %s: %w", s.path, err)
	}
	return creds, nil
}

// Save writes the credentials with mode 0600, by a rename so a reader never sees half a file.
func (s *Store) Save(creds Credentials) error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return fmt.Errorf("create %s: %w", filepath.Dir(s.path), err)
	}
	data, err := json.MarshalIndent(creds, "", "  ") // #nosec G117 -- the tokens are stored on purpose, in a file only the user can read
	if err != nil {
		return fmt.Errorf("encode the credentials: %w", err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(s.path), ".credentials-*")
	if err != nil {
		return fmt.Errorf("write the credentials: %w", err)
	}
	tmpName := tmp.Name()
	if _, err := tmp.Write(data); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmpName)
		return fmt.Errorf("write the credentials: %w", err)
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("write the credentials: %w", err)
	}
	if err := os.Rename(tmpName, s.path); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("write the credentials: %w", err)
	}
	return nil
}

// Delete removes the credentials; no file is not an error.
func (s *Store) Delete() error {
	if err := os.Remove(s.path); err != nil && !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("delete %s: %w", s.path, err)
	}
	return nil
}

// refreshMargin renews a token this long before it expires, so a call never leaves with a token
// that expires on the way.
const refreshMargin = 30 * time.Second

// TokenSource hands out a valid access token, refreshing and storing it when it is about to
// expire. It is safe for concurrent use, so parallel calls refresh once.
type TokenSource struct {
	mu    sync.Mutex
	store *Store
	creds Credentials
	flow  *DeviceFlow
	now   func() time.Time
}

// NewTokenSource returns a source over stored credentials.
func NewTokenSource(store *Store, creds Credentials) *TokenSource {
	return &TokenSource{store: store, creds: creds, flow: NewDeviceFlow(creds.Issuer, creds.ClientID), now: time.Now}
}

// Token returns an access token valid for at least refreshMargin.
func (t *TokenSource) Token(ctx context.Context) (string, error) {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.now().Add(refreshMargin).Before(t.creds.ExpiresAt) {
		return t.creds.AccessToken, nil
	}
	if t.creds.RefreshToken == "" {
		return "", ErrNotSignedIn
	}
	token, err := t.flow.Refresh(ctx, TokenEndpoint(t.creds.Issuer), t.creds.RefreshToken)
	if err != nil {
		return "", err
	}
	t.creds.AccessToken = token.AccessToken
	if token.RefreshToken != "" {
		t.creds.RefreshToken = token.RefreshToken
	}
	t.creds.ExpiresAt = t.now().Add(time.Duration(token.ExpiresIn) * time.Second)
	if err := t.store.Save(t.creds); err != nil {
		return "", fmt.Errorf("store the refreshed sign-in: %w", err)
	}
	return t.creds.AccessToken, nil
}
