// Package apidocs reads the request schemas of the Oba API from its live OpenAPI document
// (/v3/api-docs), so that the CLI checks keys and prints field help from the deployed API and
// never from a list that can fall behind it.
package apidocs

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/httplog"
)

// Path is where springdoc serves the OpenAPI document. It is public: no token is sent.
const Path = "/v3/api-docs"

const (
	fetchTimeout    = 30 * time.Second
	maxDocBytes     = 20 * 1024 * 1024
	suggestDistance = 3
	schemaRefPrefix = "#/components/schemas/"
	maxRefDepth     = 16
)

// Field is one property of a request schema.
type Field struct {
	Name     string
	Required bool
	Type     string
	Enum     []string
	Nullable bool
	// MaxLength is the most characters a string may have; 0 means no limit is documented.
	MaxLength int
}

// Doc is a fetched OpenAPI document, reduced to its component schemas.
type Doc struct {
	schemas map[string]json.RawMessage
}

// node is the part of a schema object the reader needs. Type is a string in OpenAPI 3.0 and a
// string or an array in 3.1, where "null" in the array marks a nullable value.
type node struct {
	Ref        string            `json:"$ref"`
	Type       json.RawMessage   `json:"type"`
	Format     string            `json:"format"`
	Enum       []json.RawMessage `json:"enum"`
	Nullable   bool              `json:"nullable"`
	AllOf      []json.RawMessage `json:"allOf"`
	AnyOf      []json.RawMessage `json:"anyOf"`
	OneOf      []json.RawMessage `json:"oneOf"`
	Properties json.RawMessage   `json:"properties"`
	Required   []string          `json:"required"`
	MaxLength  int               `json:"maxLength"`
}

// Fetch reads the OpenAPI document of the API at apiURL.
func Fetch(apiURL string) (*Doc, error) {
	url := strings.TrimRight(apiURL, "/") + Path
	httpClient := &http.Client{Timeout: fetchTimeout, Transport: httplog.Transport}
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxDocBytes))
	if err != nil {
		return nil, fmt.Errorf("failed to read the response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("the API answered %d %s", resp.StatusCode, http.StatusText(resp.StatusCode))
	}
	return Parse(body)
}

// Parse reads an OpenAPI document from its JSON text.
func Parse(data []byte) (*Doc, error) {
	var doc struct {
		Components struct {
			Schemas map[string]json.RawMessage `json:"schemas"`
		} `json:"components"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		return nil, fmt.Errorf("the document is not valid JSON: %w", err)
	}
	if len(doc.Components.Schemas) == 0 {
		return nil, errors.New("the document has no component schemas")
	}
	return &Doc{schemas: doc.Components.Schemas}, nil
}

// Fields returns the properties of the named schema in the order the document lists them,
// which is the order of the request record in the backend.
func (d *Doc) Fields(schema string) ([]Field, error) {
	raw, ok := d.schemas[schema]
	if !ok {
		return nil, fmt.Errorf("the document has no schema %q", schema)
	}
	var s node
	if err := json.Unmarshal(raw, &s); err != nil {
		return nil, fmt.Errorf("schema %q is not a schema object: %w", schema, err)
	}
	if len(s.Properties) == 0 {
		return nil, fmt.Errorf("schema %q has no properties", schema)
	}
	names, props, err := orderedObject(s.Properties)
	if err != nil {
		return nil, fmt.Errorf("the properties of schema %q: %w", schema, err)
	}
	required := make(map[string]bool, len(s.Required))
	for _, name := range s.Required {
		required[name] = true
	}

	fields := make([]Field, 0, len(names))
	for _, name := range names {
		field, err := d.describe(props[name], 0)
		if err != nil {
			return nil, fmt.Errorf("property %q of schema %q: %w", name, schema, err)
		}
		field.Name = name
		field.Required = required[name]
		fields = append(fields, field)
	}
	return fields, nil
}

// describe resolves a property schema to its type, enum and nullability. It follows $ref and
// merges allOf, anyOf and oneOf, because springdoc writes an enum inline or as a reference
// depending on its settings and its OpenAPI version.
func (d *Doc) describe(raw json.RawMessage, depth int) (Field, error) {
	if depth > maxRefDepth {
		return Field{}, errors.New("schema references nest too deep")
	}
	var n node
	if err := json.Unmarshal(raw, &n); err != nil {
		return Field{}, err
	}

	var field Field
	if n.Ref != "" {
		target, ok := d.schemas[strings.TrimPrefix(n.Ref, schemaRefPrefix)]
		if !strings.HasPrefix(n.Ref, schemaRefPrefix) || !ok {
			return Field{}, fmt.Errorf("unresolved reference %q", n.Ref)
		}
		resolved, err := d.describe(target, depth+1)
		if err != nil {
			return Field{}, err
		}
		field = resolved
	}

	typeName, nullable, err := parseType(n.Type)
	if err != nil {
		return Field{}, err
	}
	if typeName != "" {
		field.Type = typeName
		if n.Format != "" {
			field.Type += " (" + n.Format + ")"
		}
	}
	field.Nullable = field.Nullable || nullable || n.Nullable
	if n.MaxLength > 0 {
		field.MaxLength = n.MaxLength
	}

	if len(n.Enum) > 0 {
		field.Enum = nil
		for _, value := range n.Enum {
			// null first: encoding/json decodes null into a string as "" without an error.
			if string(bytes.TrimSpace(value)) == "null" {
				field.Nullable = true
				continue
			}
			var s string
			if json.Unmarshal(value, &s) == nil {
				field.Enum = append(field.Enum, s)
				continue
			}
			field.Enum = append(field.Enum, string(value))
		}
	}

	for _, group := range [][]json.RawMessage{n.AllOf, n.AnyOf, n.OneOf} {
		for _, part := range group {
			sub, err := d.describe(part, depth+1)
			if err != nil {
				return Field{}, err
			}
			if sub.Type == "null" {
				field.Nullable = true
				continue
			}
			if field.Type == "" {
				field.Type = sub.Type
			}
			if len(field.Enum) == 0 {
				field.Enum = sub.Enum
			}
			if field.MaxLength == 0 {
				field.MaxLength = sub.MaxLength
			}
			field.Nullable = field.Nullable || sub.Nullable
		}
	}
	return field, nil
}

// parseType reads "type" as a string or as an array of strings; "null" in the array marks the
// value nullable and the other entry is the type.
func parseType(raw json.RawMessage) (string, bool, error) {
	if len(raw) == 0 {
		return "", false, nil
	}
	var single string
	if json.Unmarshal(raw, &single) == nil {
		return single, false, nil
	}
	var many []string
	if err := json.Unmarshal(raw, &many); err != nil {
		return "", false, fmt.Errorf("type is neither a string nor an array of strings: %s", raw)
	}
	nullable := false
	var types []string
	for _, t := range many {
		if t == "null" {
			nullable = true
			continue
		}
		types = append(types, t)
	}
	if len(types) == 0 && nullable {
		return "null", false, nil
	}
	return strings.Join(types, " | "), nullable, nil
}

// orderedObject decodes a JSON object and also returns its keys in document order, which
// encoding/json maps do not keep.
func orderedObject(raw json.RawMessage) ([]string, map[string]json.RawMessage, error) {
	dec := json.NewDecoder(bytes.NewReader(raw))
	tok, err := dec.Token()
	if err != nil {
		return nil, nil, err
	}
	if delim, ok := tok.(json.Delim); !ok || delim != '{' {
		return nil, nil, errors.New("not a JSON object")
	}
	var names []string
	values := make(map[string]json.RawMessage)
	for dec.More() {
		tok, err := dec.Token()
		if err != nil {
			return nil, nil, err
		}
		name, ok := tok.(string)
		if !ok {
			return nil, nil, errors.New("object key is not a string")
		}
		var value json.RawMessage
		if err := dec.Decode(&value); err != nil {
			return nil, nil, err
		}
		if _, seen := values[name]; !seen {
			names = append(names, name)
		}
		values[name] = value
	}
	return names, values, nil
}

// Suggest returns the name closest to key by edit distance, ignoring case, or "" when none is
// within distance 3. A tie goes to the name listed first.
func Suggest(key string, names []string) string {
	best := ""
	bestDistance := suggestDistance + 1
	lowerKey := strings.ToLower(key)
	for _, name := range names {
		distance := levenshtein(lowerKey, strings.ToLower(name))
		if distance < bestDistance {
			best = name
			bestDistance = distance
		}
	}
	return best
}

func levenshtein(a, b string) int {
	ra, rb := []rune(a), []rune(b)
	prev := make([]int, len(rb)+1)
	curr := make([]int, len(rb)+1)
	for j := range prev {
		prev[j] = j
	}
	for i := 1; i <= len(ra); i++ {
		curr[0] = i
		for j := 1; j <= len(rb); j++ {
			cost := 1
			if ra[i-1] == rb[j-1] {
				cost = 0
			}
			curr[j] = min(prev[j]+1, curr[j-1]+1, prev[j-1]+cost)
		}
		prev, curr = curr, prev
	}
	return prev[len(rb)]
}
