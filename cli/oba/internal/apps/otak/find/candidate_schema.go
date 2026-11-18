package find

import (
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
)

// MaxCandidates is the most candidates one run may return; the output schema enforces it.
const MaxCandidates = 10

// SummaryMaxLength is the most characters of a candidate's summary, the one line the pick list
// shows for it.
const SummaryMaxLength = 80

// Candidate is one company Claude Code proposes. City and Size are nil when Claude Code could
// not find them. Contacts are the business contacts found for it, as contact.Clean left them.
type Candidate struct {
	Name     string                 `json:"name"`
	Website  string                 `json:"website"`
	Country  string                 `json:"country"`
	Reason   string                 `json:"reason"`
	Summary  string                 `json:"summary"`
	City     *string                `json:"city"`
	Size     *string                `json:"size"`
	Contacts []contact.FoundContact `json:"contacts"`
}

type output struct {
	Candidates []Candidate `json:"candidates"`
}

// Schema returns the JSON Schema of the final output: {candidates: [{name, website, country,
// reason, summary, city, size, contacts}]} with at most MaxCandidates items, and per candidate at
// most contact.MaxFound contacts {name, role, email, phone, sourceUrl}. country is limited to the
// live country codes and size to the live sizes or null; with no values given, either is any
// string. city is a string or null.
func Schema(enums LeadEnums) (string, error) {
	country := map[string]any{"type": "string"}
	if len(enums.Countries) > 0 {
		country["enum"] = enums.Countries
	}
	size := map[string]any{"type": []string{"string", "null"}}
	if len(enums.Sizes) > 0 {
		values := make([]any, 0, len(enums.Sizes)+1)
		for _, value := range enums.Sizes {
			values = append(values, value)
		}
		size["enum"] = append(values, nil)
	}
	schema := map[string]any{
		"type":                 "object",
		"additionalProperties": false,
		"required":             []string{"candidates"},
		"properties": map[string]any{
			"candidates": map[string]any{
				"type":     "array",
				"maxItems": MaxCandidates,
				"items": map[string]any{
					"type":                 "object",
					"additionalProperties": false,
					"required":             []string{"name", "website", "country", "reason", "summary", "city", "size", "contacts"},
					"properties": map[string]any{
						"name":     map[string]any{"type": "string"},
						"website":  map[string]any{"type": "string"},
						"country":  country,
						"reason":   map[string]any{"type": "string"},
						"summary":  map[string]any{"type": "string", "maxLength": SummaryMaxLength},
						"city":     map[string]any{"type": []string{"string", "null"}},
						"size":     size,
						"contacts": contact.SearchSchema(),
					},
				},
			},
		},
	}
	out, err := json.Marshal(schema)
	if err != nil {
		return "", fmt.Errorf("failed to build the output schema: %w", err)
	}
	return string(out), nil
}
