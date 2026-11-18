// Package config resolves where the CLI talks to: the Oba API that serves Kalkan, the Keycloak
// issuer and client of the CLI sign-in, and the organization a call acts in.
package config

import (
	"os"
	"strings"
)

const (
	// ProdAPIURL is the public Oba API, which serves Kalkan under /kalkan.
	ProdAPIURL = "https://api.korur.com"
	// ProdIssuer is the sensey realm of the shared Keycloak, the one realm of every Oba app.
	ProdIssuer = "https://auth.korur.com/realms/sensey"
	// DevAPIURL is the backend of the local Oba stack.
	DevAPIURL = "http://localhost:8080"
	// DevIssuer is the sensey realm of the local Keycloak.
	DevIssuer = "http://localhost:8086/realms/sensey"
	// ClientID is the public Keycloak client the CLI signs in with, by device authorization.
	ClientID = "kalkan-cli"

	// EnvAPIURL overrides the API URL.
	EnvAPIURL = "KALKAN_API_URL"
	// EnvIssuer overrides the Keycloak issuer URL.
	EnvIssuer = "KALKAN_ISSUER"
	// EnvClientID overrides the Keycloak client id.
	EnvClientID = "KALKAN_CLIENT_ID"
	// EnvOrgID overrides the organization of every call.
	EnvOrgID = "KALKAN_ORG_ID"
)

// Overrides holds the values given on the command line; an empty field is not given.
type Overrides struct {
	APIURL   string
	Issuer   string
	ClientID string
	OrgID    string
	Dev      bool
}

// Config is the resolved configuration of one CLI run.
type Config struct {
	APIURL   string
	Issuer   string
	ClientID string
	OrgID    string
}

// Resolve applies, per field, the command line first, then the local URLs when Dev is set,
// then the environment, then the values stored at sign-in, then the production defaults. The
// organization of the repository's kalkan.yaml (projectOrgID, empty when there is none) sits
// between the environment and the stored sign-in, so a repository pushes to its own organization
// whichever organization its developer last chose. The stored values come before the defaults so
// that a sign-in against the local stack keeps calling the local stack. OrgID has no default.
func Resolve(o Overrides, projectOrgID string, stored Config) Config {
	devAPIURL, devIssuer := "", ""
	if o.Dev {
		devAPIURL, devIssuer = DevAPIURL, DevIssuer
	}
	return Config{
		APIURL:   strings.TrimRight(first(o.APIURL, devAPIURL, os.Getenv(EnvAPIURL), stored.APIURL, ProdAPIURL), "/"),
		Issuer:   strings.TrimRight(first(o.Issuer, devIssuer, os.Getenv(EnvIssuer), stored.Issuer, ProdIssuer), "/"),
		ClientID: first(o.ClientID, os.Getenv(EnvClientID), stored.ClientID, ClientID),
		OrgID:    first(o.OrgID, os.Getenv(EnvOrgID), projectOrgID, stored.OrgID),
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
