// Package config resolves where the CLI talks to (the Oba API, the Keycloak issuer and client
// of the sign-in) and keeps the local settings file, which holds the chosen organization.
package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

const (
	// ProdAPIURL is the public Oba API.
	ProdAPIURL = "https://api.korur.com"
	// ProdIssuer is the sensey realm of the shared Keycloak, the one realm of every Oba app.
	ProdIssuer = "https://auth.korur.com/realms/sensey"
	// ProdClientID is the public Keycloak client the CLI signs in with, by device authorization.
	ProdClientID = "oba-cli"

	// EnvAPIURL overrides the API URL.
	EnvAPIURL = "OBA_API_URL"
	// EnvIssuer overrides the Keycloak issuer URL.
	EnvIssuer = "OBA_ISSUER"
	// EnvClientID overrides the Keycloak client id.
	EnvClientID = "OBA_CLIENT_ID"
	// EnvToken, when set, is the bearer token of every call, and the stored session is not
	// read: a script or an agent signs in without the browser.
	EnvToken = "OBA_TOKEN"
)

// Endpoints names the API and the Keycloak client a session belongs to.
type Endpoints struct {
	APIURL   string
	Issuer   string
	ClientID string
}

// Resolve applies, per field, the environment first, then the values stored with the session,
// then the production defaults. The stored values come before the defaults so that a sign-in
// against the local stack keeps calling the local stack; the environment still wins, so one
// command can be pointed elsewhere without signing in again. Login passes no stored values.
func Resolve(stored Endpoints) Endpoints {
	return Endpoints{
		APIURL:   strings.TrimRight(first(os.Getenv(EnvAPIURL), stored.APIURL, ProdAPIURL), "/"),
		Issuer:   strings.TrimRight(first(os.Getenv(EnvIssuer), stored.Issuer, ProdIssuer), "/"),
		ClientID: first(os.Getenv(EnvClientID), stored.ClientID, ProdClientID),
	}
}

func first(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

// Dir returns ~/.config/oba, the directory of the settings file, the plaintext credentials
// fallback and the keyring marker.
func Dir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("home directory unavailable: %w", err)
	}
	return filepath.Join(home, ".config", "oba"), nil
}

// File is the content of ~/.config/oba/config.json. It outlives a session: logout keeps it.
type File struct {
	// OrgID is the organization chosen with `oba org use`; empty means the login default.
	OrgID string `json:"org-id,omitempty"`
	// KazanBoards maps an organization id to the id of the Kazan board chosen with
	// `oba kazan board use` in it. Boards belong to one organization, so each organization keeps
	// its own choice and switching organization never points at another one's board.
	KazanBoards map[string]string `json:"kazan-board,omitempty"`
}

func filePath() (string, error) {
	dir, err := Dir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "config.json"), nil
}

// Load reads the settings file. A missing file is an empty File, not an error.
func Load() (File, error) {
	path, err := filePath()
	if err != nil {
		return File{}, err
	}
	data, err := os.ReadFile(path) //nolint:gosec // the path is fixed under the user's home directory
	if err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			return File{}, nil
		}
		return File{}, fmt.Errorf("failed to read %s: %w", path, err)
	}
	var f File
	if err := json.Unmarshal(data, &f); err != nil {
		return File{}, fmt.Errorf("failed to parse %s: %w", path, err)
	}
	return f, nil
}

// Save writes the settings file with mode 0600 in a 0700 directory.
func Save(f File) error {
	path, err := filePath()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("failed to create %s: %w", filepath.Dir(path), err)
	}
	data, err := json.MarshalIndent(f, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to serialize settings: %w", err)
	}
	if err := os.WriteFile(path, append(data, '\n'), 0o600); err != nil {
		return fmt.Errorf("failed to write %s: %w", path, err)
	}
	return nil
}

// CurrentOrgID returns the organization relation calls act in: the one chosen in the settings
// file, else the default organization stored at login. Empty means neither is set.
func CurrentOrgID(defaultOrgID string) (string, error) {
	f, err := Load()
	if err != nil {
		return "", err
	}
	return first(f.OrgID, defaultOrgID), nil
}
