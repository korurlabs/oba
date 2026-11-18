package shrine

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// membership is one organization of GET /users/me as the API sent it, with the id read out to
// find it by.
type membership struct {
	id  string
	raw json.RawMessage
}

// recordOrganizations makes the signed-in user's organizations the --json result of oba org
// list. session.CurrentUser keeps only the fields the table shows and records the whole user, so
// this reads /users/me once more, unrecorded and only while --json is on, to give the
// organizations with every field the API sends.
func recordOrganizations() error {
	if !jsonout.Enabled() {
		return nil
	}
	memberships, err := readMemberships()
	if err != nil {
		return err
	}
	items := make([]json.RawMessage, 0, len(memberships))
	for _, m := range memberships {
		items = append(items, m.raw)
	}
	jsonresult.SetList(items)
	return nil
}

// recordOrganization makes the organization orgID, as the API sent it, the --json result of oba
// org use, for the same reason as recordOrganizations.
func recordOrganization(orgID string) error {
	if !jsonout.Enabled() {
		return nil
	}
	memberships, err := readMemberships()
	if err != nil {
		return err
	}
	for _, m := range memberships {
		if strings.EqualFold(m.id, orgID) {
			return jsonresult.Set(m.raw)
		}
	}
	return fmt.Errorf("organization %s is no longer among your organizations", orgID)
}

func readMemberships() ([]membership, error) {
	c, err := session.UserClient()
	if err != nil {
		return nil, err
	}
	body, err := c.WithoutRecording().Get("/users/me")
	if err != nil {
		return nil, fmt.Errorf("failed to read your organizations: %w", err)
	}
	var resp struct {
		Data struct {
			Organizations []json.RawMessage `json:"organizations"`
		} `json:"data"`
	}
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to parse the user profile: %w", err)
	}
	memberships := make([]membership, 0, len(resp.Data.Organizations))
	for _, raw := range resp.Data.Organizations {
		var org struct {
			ID string `json:"id"`
		}
		if err := json.Unmarshal(raw, &org); err != nil {
			return nil, fmt.Errorf("failed to parse the user profile: %w", err)
		}
		memberships = append(memberships, membership{id: org.ID, raw: raw})
	}
	return memberships, nil
}
