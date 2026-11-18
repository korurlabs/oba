package find

import (
	"strings"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// leadRequest builds the create body of one candidate. A website the API would refuse, a
// country code or size it does not know and a city over its limit are left out, so that the
// lead is still created without them.
func leadRequest(candidate Candidate, icpID string, allowed allowedValues) client.LeadCreateRequest {
	website := strings.TrimSpace(candidate.Website)
	if len(website) > websiteMaxLength || !relation.ValidWebsite(website) {
		website = ""
	}
	country := strings.TrimSpace(candidate.Country)
	if !allowed.countries[country] {
		country = ""
	}
	city := strings.TrimSpace(optional(candidate.City))
	if utf8.RuneCountInString(city) > cityMaxRunes {
		city = ""
	}
	size := strings.TrimSpace(optional(candidate.Size))
	if !allowed.sizes[size] {
		size = ""
	}
	notes := []rune(foundNotesPrefix + strings.TrimSpace(candidate.Reason))
	if len(notes) > notesMaxRunes {
		notes = notes[:notesMaxRunes]
	}
	return client.LeadCreateRequest{
		Name:    strings.TrimSpace(candidate.Name),
		Type:    leadType,
		Stage:   pipelineStageNew,
		Source:  sourceResearch,
		Website: website,
		Country: country,
		City:    city,
		Size:    size,
		IcpID:   icpID,
		Notes:   string(notes),
	}
}
