package contact

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// MaxFillPerSearch is the most relations one Claude Code run of contact fill searches. A run
// over more would grow the brief and the time to minutes per company without a sign of life.
const MaxFillPerSearch = 10

// DefaultFillRole is the role contact fill prefers when the user names none.
const DefaultFillRole = "the owner, a managing director or the person responsible for IT"

// FillBrief is the task of one contact fill search: the relations by id, name and website,
// the contacts each already has, and the contact rules find uses. role is the role to prefer.
func FillBrief(targets []FillTarget, role string) string {
	var b strings.Builder
	b.WriteString("Find the business contacts of each company below. The companies are already known to us; do not look for other companies.\n\n")
	b.WriteString("Companies (relation id - name - website):\n")
	for _, target := range targets {
		fmt.Fprintf(&b, "- %s - %s - %s\n", target.ID, guardrail.OneLine(target.Name), guardrail.OneLine(target.Website))
		if len(target.Existing) > 0 {
			fmt.Fprintf(&b, "  Already known contacts, do not propose them again: %s\n", guardrail.OneLine(strings.Join(target.Existing, ", ")))
		}
	}
	b.WriteString(`
Rules:
- Return one entry per company above, with its relationId exactly as given, and no other entries.
- Search only the company's own website and public business registers; check every page with a page fetch.
`)
	b.WriteString(SearchRules(role))
	return b.String()
}

// FillSchema is the JSON Schema of the final output of a contact fill search:
// {relations: [{relationId, contacts: [{name, role, email, phone, sourceUrl}]}]} with at most
// MaxFillPerSearch relations and MaxFound contacts per relation.
func FillSchema() (string, error) {
	schema := map[string]any{
		"type":                 "object",
		"additionalProperties": false,
		"required":             []string{"relations"},
		"properties": map[string]any{
			"relations": map[string]any{
				"type":     "array",
				"maxItems": MaxFillPerSearch,
				"items": map[string]any{
					"type":                 "object",
					"additionalProperties": false,
					"required":             []string{"relationId", "contacts"},
					"properties": map[string]any{
						"relationId": map[string]any{"type": "string"},
						"contacts":   SearchSchema(),
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
