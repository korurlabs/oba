package session

import (
	"strings"
	"sync"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

var (
	overrideMu sync.Mutex
	override   string
)

// UseOrganization makes one run act in the organization value names, by id or by name, instead
// of the chosen one, without storing it; empty restores the chosen one. The root command sets it
// from --org before every run.
func UseOrganization(value string) {
	overrideMu.Lock()
	defer overrideMu.Unlock()
	override = strings.TrimSpace(value)
}

func organizationOverride() string {
	overrideMu.Lock()
	defer overrideMu.Unlock()
	return override
}

// CurrentOrgID returns the organization a command acts in, among orgs, the signed-in user's
// memberships: the one --org names, else the one chosen with 'oba org use', else defaultOrgID.
// Empty means none is set.
func CurrentOrgID(defaultOrgID string, orgs []client.Organization) (string, error) {
	if value := organizationOverride(); value != "" {
		org, err := organization.Match(orgs, value)
		if err != nil {
			return "", err
		}
		return org.ID, nil
	}
	return config.CurrentOrgID(defaultOrgID)
}

// organizationID returns the organization the calls of s act in. --org given as an id needs no
// call, and the API refuses an id the user is not a member of; given as a name it is matched against the user's memberships. Without --org it is the
// chosen organization, else the default one: stored with the session, or for OBA_TOKEN read
// from /users/me, since no sign-in stored it.
func (s signIn) organizationID() (string, error) {
	if value := organizationOverride(); value != "" {
		if userinput.IsID(value) {
			return value, nil
		}
		u, err := user(s.client("").WithoutRecording())
		if err != nil {
			return "", err
		}
		return CurrentOrgID("", u.Organizations)
	}
	orgID, err := config.CurrentOrgID(s.creds.DefaultOrgID)
	if err != nil || orgID != "" || !s.envToken {
		return orgID, err
	}
	u, err := user(s.client("").WithoutRecording())
	if err != nil {
		return "", err
	}
	if u.DefaultOrganizationID == nil {
		return "", nil
	}
	return *u.DefaultOrganizationID, nil
}
