package contact

// MaxFound is the most business contacts a search may return for one company; SearchSchema and
// Clean both enforce it.
const MaxFound = 2

// FoundContact is a person Claude Code found for a company on a public business source. Role,
// Email and Phone are nil when the source gives none, or when Clean refused the value.
// SourceURL is the page the person was found on, which the person must be told at first contact.
type FoundContact struct {
	Name      string  `json:"name"`
	Role      *string `json:"role"`
	Email     *string `json:"email"`
	Phone     *string `json:"phone"`
	SourceURL string  `json:"sourceUrl"`
}

// SearchSchema is the JSON Schema of the contacts of one company in a search's output: at most
// MaxFound items {name, role, email, phone, sourceUrl}.
func SearchSchema() map[string]any {
	return map[string]any{
		"type":     "array",
		"maxItems": MaxFound,
		"items": map[string]any{
			"type":                 "object",
			"additionalProperties": false,
			"required":             []string{"name", "role", "email", "phone", "sourceUrl"},
			"properties": map[string]any{
				"name":      map[string]any{"type": "string"},
				"role":      map[string]any{"type": []string{"string", "null"}},
				"email":     map[string]any{"type": []string{"string", "null"}},
				"phone":     map[string]any{"type": []string{"string", "null"}},
				"sourceUrl": map[string]any{"type": "string"},
			},
		},
	}
}
