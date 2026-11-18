package apidocs

import (
	"encoding/json"
	"fmt"
	"strings"
)

// jsonSchemaTypes are the type names JSON Schema knows; a type the document gives that is not
// one of them is left out rather than sent as an invalid schema.
var jsonSchemaTypes = map[string]bool{
	"string": true, "integer": true, "number": true, "boolean": true, "object": true, "array": true,
}

// Schema returns the JSON Schema of one object with exactly the given fields: each with its
// type, its allowed values and its maximum length, null allowed where the field is nullable,
// the required fields required and no other key allowed. Built from the live API document, it
// describes an object the API accepts, so that what a generator returns fits the request.
func Schema(fields []Field) (string, error) {
	properties := make(map[string]any, len(fields))
	required := make([]string, 0, len(fields))
	for _, field := range fields {
		properties[field.Name] = property(field)
		if field.Required {
			required = append(required, field.Name)
		}
	}
	schema := map[string]any{
		"type":                 "object",
		"additionalProperties": false,
		"required":             required,
		"properties":           properties,
	}
	out, err := json.Marshal(schema)
	if err != nil {
		return "", fmt.Errorf("failed to build the JSON schema: %w", err)
	}
	return string(out), nil
}

func property(field Field) map[string]any {
	prop := map[string]any{}
	types := schemaTypes(field.Type)
	if len(types) > 0 {
		if field.Nullable {
			types = append(types, "null")
		}
		if len(types) == 1 {
			prop["type"] = types[0]
		} else {
			prop["type"] = types
		}
	}
	if len(field.Enum) > 0 {
		values := make([]any, 0, len(field.Enum)+1)
		for _, value := range field.Enum {
			values = append(values, enumValue(value, types))
		}
		if field.Nullable {
			values = append(values, nil)
		}
		prop["enum"] = values
	}
	if field.MaxLength > 0 {
		prop["maxLength"] = field.MaxLength
	}
	return prop
}

// schemaTypes reads a Field type such as "string", "string (uuid)" or "integer | string" into
// JSON Schema type names, dropping the format.
func schemaTypes(fieldType string) []string {
	var types []string
	for _, part := range strings.Split(fieldType, " | ") {
		name, _, _ := strings.Cut(strings.TrimSpace(part), " (")
		if jsonSchemaTypes[name] {
			types = append(types, name)
		}
	}
	return types
}

// enumValue returns an allowed value as JSON Schema needs it. Field keeps a value that is not a
// string as its JSON text, so a field that is not a string type gets that text decoded back.
func enumValue(value string, types []string) any {
	for _, t := range types {
		if t == "string" {
			return value
		}
	}
	var decoded any
	if len(types) > 0 && json.Unmarshal([]byte(value), &decoded) == nil {
		return decoded
	}
	return value
}
