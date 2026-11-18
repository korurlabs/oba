package apidocs

import (
	"encoding/json"
	"fmt"
	"sort"
	"strings"
)

// CheckKeys refuses the first unknown key, in sorted order so that the error is stable, with
// the closest field name when one is near enough to be a typo.
func CheckKeys(obj map[string]json.RawMessage, fields []Field) error {
	names := make([]string, 0, len(fields))
	known := make(map[string]bool, len(fields))
	for _, field := range fields {
		names = append(names, field.Name)
		known[field.Name] = true
	}
	keys := make([]string, 0, len(obj))
	for key := range obj {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	for _, key := range keys {
		if known[key] {
			continue
		}
		if suggestion := Suggest(key, names); suggestion != "" {
			return fmt.Errorf("unknown field %q. Did you mean %q?", key, suggestion) //nolint:staticcheck // ST1005: the question is the hint
		}
		return fmt.Errorf("unknown field %q", key)
	}
	return nil
}

// Template renders the fields as a JSON-shaped template: each key with its type or its allowed
// values, and "(required)" where the schema requires it and markRequired is set.
func Template(fields []Field, markRequired bool) string {
	lines := make([]string, 0, len(fields)+2)
	lines = append(lines, "  {")
	for i, field := range fields {
		value := field.Type
		if len(field.Enum) > 0 {
			quoted := make([]string, len(field.Enum))
			for j, option := range field.Enum {
				quoted[j] = fmt.Sprintf("%q", option)
			}
			value = strings.Join(quoted, " | ")
		}
		if value == "" {
			value = "any"
		}
		if field.Nullable {
			value += " | null"
		}
		line := fmt.Sprintf("    %q: %s", field.Name, value)
		if i < len(fields)-1 {
			line += ","
		}
		if markRequired && field.Required {
			line += "  (required)"
		}
		lines = append(lines, line)
	}
	lines = append(lines, "  }")
	return strings.Join(lines, "\n")
}

// FieldListError reports that the field list of the API at apiURL could not be read.
func FieldListError(apiURL string, err error) error {
	return fmt.Errorf("Could not read the field list from %s%s: %w", strings.TrimRight(apiURL, "/"), Path, err) //nolint:staticcheck // ST1005: shown to the user verbatim
}

// FetchDoc reads the document of the API at apiURL; a failure is a FieldListError.
func FetchDoc(apiURL string) (*Doc, error) {
	doc, err := Fetch(apiURL)
	if err != nil {
		return nil, FieldListError(apiURL, err)
	}
	return doc, nil
}
