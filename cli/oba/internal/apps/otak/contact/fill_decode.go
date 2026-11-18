package contact

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// FillResult is what one search found for one relation, as Clean left it.
type FillResult struct {
	Target   FillTarget
	Contacts []FoundContact
}

type fillOutput struct {
	Relations []struct {
		RelationID string         `json:"relationId"`
		Contacts   []FoundContact `json:"contacts"`
	} `json:"relations"`
}

// DecodeFill reads the contacts Claude Code found, one result per target in the order of
// targets. A relation id that was not asked for is ignored, and a target Claude Code left out
// gets no contacts. Each relation's contacts are cleaned against its website, and a contact
// that has the name of an existing contact (case ignored) is dropped.
func DecodeFill(raw json.RawMessage, targets []FillTarget) ([]FillResult, error) {
	var out fillOutput
	if err := json.Unmarshal(raw, &out); err != nil {
		return nil, fmt.Errorf("the output of Claude Code is not a contact list: %w", err)
	}
	found := make(map[string][]FoundContact, len(out.Relations))
	for _, entry := range out.Relations {
		id := strings.TrimSpace(entry.RelationID)
		if _, seen := found[id]; !seen {
			found[id] = entry.Contacts
		}
	}
	results := make([]FillResult, 0, len(targets))
	for _, target := range targets {
		existing := make(map[string]bool, len(target.Existing))
		for _, name := range target.Existing {
			existing[relation.NormalizeName(guardrail.OneLine(name))] = true
		}
		var contacts []FoundContact
		for _, contact := range Clean(target.Website, found[target.ID]) {
			if !existing[relation.NormalizeName(contact.Name)] {
				contacts = append(contacts, contact)
			}
		}
		results = append(results, FillResult{Target: target, Contacts: contacts})
	}
	return results, nil
}
