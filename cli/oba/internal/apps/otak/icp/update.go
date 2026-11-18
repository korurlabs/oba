package icp

import (
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Update changes only the given keys of ICP id: it reads the ICP, lays the given keys over its
// current values, null included, and sends every request field with PUT, since the API
// replaces them all.
func Update(c *client.Client, id string, given map[string]json.RawMessage) (client.Icp, error) {
	fields, err := LoadFields(c.BaseURL, UpdateFields)
	if err != nil {
		return client.Icp{}, err
	}
	if err := apidocs.CheckKeys(given, fields); err != nil {
		return client.Icp{}, err
	}
	current, err := c.GetIcp(id)
	if err != nil {
		return client.Icp{}, err
	}
	body, err := mergeUpdate(current, given, fields)
	if err != nil {
		return client.Icp{}, err
	}
	return c.UpdateIcp(id, body)
}

// mergeUpdate builds the PUT body: every request field with its current value and the given
// keys laid over it. A field the ICP as read does not carry, and that was not given, is
// refused: the PUT would clear it without the user asking.
func mergeUpdate(current client.Icp, given map[string]json.RawMessage, fields []apidocs.Field) (map[string]json.RawMessage, error) {
	body := make(map[string]json.RawMessage, len(fields))
	for _, field := range fields {
		if value, ok := given[field.Name]; ok {
			body[field.Name] = value
			continue
		}
		value, ok := current.Values[field.Name]
		if !ok {
			return nil, fmt.Errorf("cannot keep field %q: the ICP as read has no such field; give its value", field.Name)
		}
		body[field.Name] = value
	}
	return body, nil
}
