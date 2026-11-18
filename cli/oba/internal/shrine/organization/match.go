package organization

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Match finds the organization a user named: the id exactly first, since an id is unique, then
// the name without regard to case. A name two organizations share is refused rather than
// guessed, so that records never land in the wrong organization.
func Match(orgs []client.Organization, value string) (client.Organization, error) {
	value = strings.TrimSpace(value)
	for _, org := range orgs {
		if org.ID == value {
			return org, nil
		}
	}
	var matches []client.Organization
	for _, org := range orgs {
		if strings.EqualFold(org.Name, value) {
			matches = append(matches, org)
		}
	}
	switch len(matches) {
	case 0:
		return client.Organization{}, fmt.Errorf("no organization of yours matches %q; run 'oba org list'", value)
	case 1:
		return matches[0], nil
	default:
		ids := make([]string, 0, len(matches))
		for _, org := range matches {
			ids = append(ids, org.ID)
		}
		return client.Organization{}, fmt.Errorf("several organizations are named %q: %s; use the id instead",
			value, strings.Join(ids, ", "))
	}
}
