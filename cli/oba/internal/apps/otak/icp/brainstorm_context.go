package icp

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// BrainstormContext is what Oba already knows about the user's company when a brainstorm starts: its
// organization profile and its existing ICPs. All of it is sent to Claude Code, so it holds
// only the profile and a compact form of each ICP.
type BrainstormContext struct {
	Profile client.OrganizationProfile
	Icps    []client.Icp
}

// LoadBrainstormContext reads the context of the organization c acts in. Any failing call fails the
// whole load: a brainstorm that silently lacked the profile or the ICPs would ask for what Oba
// knows, or propose ICPs that already exist. An organization without ICPs is fine.
func LoadBrainstormContext(c *client.Client) (BrainstormContext, error) {
	profile, err := c.GetOrganization(c.OrgID)
	if err != nil {
		return BrainstormContext{}, fmt.Errorf("failed to read the organization profile: %w", err)
	}
	icps, err := c.ListIcps()
	if err != nil {
		return BrainstormContext{}, fmt.Errorf("failed to read the ICPs: %w", err)
	}
	return BrainstormContext{Profile: profile, Icps: icps}, nil
}

// profileFields are the profile fields that say something about the company, under the names
// the summary shows; the name is left out, since every organization has one.
func (x BrainstormContext) profileFields() []struct {
	name  string
	value *string
} {
	return []struct {
		name  string
		value *string
	}{
		{"description", x.Profile.Description},
		{"industry", x.Profile.Industry},
		{"company size", x.Profile.CompanySize},
		{"country", x.Profile.Country},
		{"website", x.Profile.Website},
	}
}

// filledProfile names the profile fields that hold a value.
func (x BrainstormContext) filledProfile() []string {
	var filled []string
	for _, field := range x.profileFields() {
		if text(field.value) != "" {
			filled = append(filled, field.name)
		}
	}
	return filled
}

// Describes reports whether Oba knows anything about the company: a filled profile field or an
// existing ICP. Without either, the user has to tell about the company first.
func (x BrainstormContext) Describes() bool {
	return len(x.filledProfile()) > 0 || len(x.Icps) > 0
}

// cleaned is oneLine of an optional value, nil when it is unset or holds nothing printable.
func cleaned(value *string) *string {
	line := guardrail.OneLine(text(value))
	if line == "" {
		return nil
	}
	return &line
}

// contextBrief is the context as Claude Code reads it. Each part names its fields itself, so
// that a field added to a client type never reaches the brief unseen.
type contextBrief struct {
	Organization profileBrief `json:"organization"`
	ExistingIcps []icpBrief   `json:"existingIcps"`
}

type profileBrief struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	Industry    string `json:"industry,omitempty"`
	CompanySize string `json:"companySize,omitempty"`
	Country     string `json:"country,omitempty"`
	Website     string `json:"website,omitempty"`
}

// icpBrief is an existing ICP without its description: enough for Claude Code to avoid a
// duplicate, and short enough to go with every turn.
type icpBrief struct {
	Name             string `json:"name"`
	Industry         string `json:"industry,omitempty"`
	Country          string `json:"country,omitempty"`
	CompanySizeMin   string `json:"companySizeMin,omitempty"`
	CompanySizeMax   string `json:"companySizeMax,omitempty"`
	TargetPersonRole string `json:"targetPersonRole,omitempty"`
}

// brief renders the context as indented JSON, so that a multi-line description cannot be read
// as a part of the instructions around it.
func (x BrainstormContext) brief() string {
	b := contextBrief{
		Organization: profileBrief{
			Name: strings.TrimSpace(x.Profile.Name), Description: text(x.Profile.Description),
			Industry: text(x.Profile.Industry), CompanySize: text(x.Profile.CompanySize),
			Country: text(x.Profile.Country), Website: text(x.Profile.Website),
		},
		ExistingIcps: []icpBrief{},
	}
	for _, i := range x.Icps {
		b.ExistingIcps = append(b.ExistingIcps, icpBrief{
			Name: strings.TrimSpace(i.Name), Industry: strings.TrimSpace(i.Industry), Country: text(i.Country),
			CompanySizeMin: text(i.CompanySizeMin), CompanySizeMax: text(i.CompanySizeMax),
			TargetPersonRole: text(i.TargetPersonRole),
		})
	}
	encoded, err := json.MarshalIndent(b, "", "  ")
	if err != nil {
		// Strings only always encode; the error is kept visible in the brief anyway.
		return fmt.Sprintf("(the context could not be encoded: %v)", err)
	}
	return string(encoded)
}

// text is the trimmed value of an optional string, "" when it is unset.
func text(value *string) string {
	if value == nil {
		return ""
	}
	return strings.TrimSpace(*value)
}
