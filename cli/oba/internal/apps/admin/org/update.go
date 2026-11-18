package org

import (
	"encoding/json"
	"fmt"
	"reflect"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Update changes only the given keys of the client's current organization: it reads the
// organization, lays the given keys over its current values, null included, and sends every
// other request field with PUT, since the API replaces them all. It always acts on c.OrgID,
// because the API refuses a path id other than the one in the organization header. The bool
// reports whether a call was sent: when every given key already has its value, nothing is.
func Update(c *client.Client, given map[string]json.RawMessage) (client.OrganizationProfile, bool, error) {
	fields, err := LoadFields(c.BaseURL, UpdateFields)
	if err != nil {
		return client.OrganizationProfile{}, false, err
	}
	if err := apidocs.CheckKeys(given, fields); err != nil {
		return client.OrganizationProfile{}, false, err
	}
	current, err := c.GetOrganization(c.OrgID)
	if err != nil {
		return client.OrganizationProfile{}, false, err
	}
	if !changes(current, given) {
		return current, false, nil
	}
	body, err := mergeUpdate(current, given, fields)
	if err != nil {
		return client.OrganizationProfile{}, false, err
	}
	updated, err := c.UpdateOrganization(c.OrgID, body)
	if err != nil {
		return client.OrganizationProfile{}, false, err
	}
	return updated, true, nil
}

// changes reports whether any given key differs from the organization's current value. Values
// are compared as decoded JSON, so spacing and key order do not count as a change.
func changes(current client.OrganizationProfile, given map[string]json.RawMessage) bool {
	for key, value := range given {
		old, ok := current.Values[key]
		if !ok || !sameJSON(old, value) {
			return true
		}
	}
	return false
}

func sameJSON(a, b json.RawMessage) bool {
	var left, right any
	if json.Unmarshal(a, &left) != nil || json.Unmarshal(b, &right) != nil {
		return false
	}
	return reflect.DeepEqual(left, right)
}

// mergeUpdate builds the PUT body: every update field with its current value and the given keys
// laid over it. A field the organization as read does not carry, and that was not given, is
// refused: the PUT would clear it without the user asking.
func mergeUpdate(current client.OrganizationProfile, given map[string]json.RawMessage, fields []apidocs.Field) (map[string]json.RawMessage, error) {
	body := make(map[string]json.RawMessage, len(fields))
	for _, field := range fields {
		if value, ok := given[field.Name]; ok {
			body[field.Name] = value
			continue
		}
		value, ok := current.Values[field.Name]
		if !ok {
			return nil, fmt.Errorf("cannot keep field %q: the organization as read has no such field; give its value", field.Name)
		}
		body[field.Name] = value
	}
	return body, nil
}
