package contact

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"reflect"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ErrSourceURLOnCreateOnly is printed as it is: an update never changes the source URL.
var ErrSourceURLOnCreateOnly = errors.New("sourceUrl is set on create only")

// Update changes only the given keys of contact contactID: it reads the contacts of the
// relation, lays the given keys over the contact's current values, null included, and sends
// every other request field with PUT, since the API replaces them all. sourceUrl is refused. The
// bool reports whether a call was sent: when every given key already has its value, nothing is.
func Update(c *client.Client, relationID, contactID string, given map[string]json.RawMessage) (client.Contact, bool, error) {
	if _, ok := given[fieldSourceURL]; ok {
		return client.Contact{}, false, ErrSourceURLOnCreateOnly
	}
	fields, err := LoadFields(c.BaseURL, UpdateFields)
	if err != nil {
		return client.Contact{}, false, err
	}
	if err := apidocs.CheckKeys(given, fields); err != nil {
		return client.Contact{}, false, err
	}
	contacts, err := c.ListContacts(relationID)
	if err != nil {
		return client.Contact{}, false, err
	}
	current, err := find(contacts, relationID, contactID)
	if err != nil {
		return client.Contact{}, false, err
	}
	if !changes(current, given) {
		return current, false, nil
	}
	body, err := mergeUpdate(current, given, fields)
	if err != nil {
		return client.Contact{}, false, err
	}
	updated, err := c.UpdateContact(relationID, contactID, body)
	if err != nil {
		return client.Contact{}, false, err
	}
	return updated, true, nil
}

// find picks contactID from the relation's contacts. An absent contact is a not-found error, so
// that the exit status is the one the API gives for an unknown contact.
func find(contacts []client.Contact, relationID, contactID string) (client.Contact, error) {
	for _, ct := range contacts {
		if strings.EqualFold(ct.ID, contactID) {
			return ct, nil
		}
	}
	return client.Contact{}, &client.APIError{
		Status:  http.StatusNotFound,
		Message: fmt.Sprintf("contact %s not found on relation %s", contactID, relationID),
	}
}

// changes reports whether any given key differs from the contact's current value. Values are
// compared as decoded JSON, so spacing and key order do not count as a change.
func changes(current client.Contact, given map[string]json.RawMessage) bool {
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

// mergeUpdate builds the PUT body: every update field with its current value and the given
// keys laid over it. A field the contact as read does not carry, and that was not given, is
// refused: the PUT would clear it without the user asking. sourceUrl is left out, since the API
// keeps the stored one. When the merged e-mail is empty and no emailType was given, emailType is
// cleared too, since the API refuses a type without an address. When the kind changes, the main
// flag that does not fit the new kind is cleared unless given, since the API refuses it.
func mergeUpdate(current client.Contact, given map[string]json.RawMessage, fields []apidocs.Field) (map[string]json.RawMessage, error) {
	body := make(map[string]json.RawMessage, len(fields))
	for _, field := range fields {
		if value, ok := given[field.Name]; ok {
			body[field.Name] = value
			continue
		}
		value, ok := current.Values[field.Name]
		if !ok {
			return nil, fmt.Errorf("cannot keep field %q: the contact as read has no such field; give its value", field.Name)
		}
		body[field.Name] = value
	}
	clearEmailTypeWithoutEmail(body, given)
	clearMainFlagOfOtherKind(current, body, given)
	return body, nil
}

// clearEmailTypeWithoutEmail sets emailType to null when the body's e-mail is null, absent or
// blank. A given emailType is the user's choice and is sent as given, so the API names the
// conflict instead of the CLI hiding it.
func clearEmailTypeWithoutEmail(body, given map[string]json.RawMessage) {
	if _, ok := body[fieldEmailType]; !ok {
		return
	}
	if _, ok := given[fieldEmailType]; ok {
		return
	}
	var email *string
	if raw, ok := body[fieldEmail]; ok {
		if err := json.Unmarshal(raw, &email); err != nil {
			// rule-10-no-op -- reason: a non-string email is invalid input the API refuses with its own error, so the body goes as given.
			return
		}
	}
	if email == nil || strings.TrimSpace(*email) == "" {
		body[fieldEmailType] = json.RawMessage(`null`)
	}
}

// clearMainFlagOfOtherKind sets the main flag of the kind the contact leaves to false when the
// merged kind differs from the current one: the stored flag would otherwise go back on a contact
// of the other kind, which the API refuses. The API itself moves the main role of the old kind to
// another contact. A given flag is the user's choice and is sent as given, so false on the main
// contact of an unchanged kind reaches the API, which refuses it. A null or absent kind changes
// nothing here; the API refuses a null kind.
func clearMainFlagOfOtherKind(current client.Contact, body, given map[string]json.RawMessage) {
	raw, ok := body[fieldKind]
	if !ok {
		return
	}
	var kind *string
	if err := json.Unmarshal(raw, &kind); err != nil {
		// rule-10-no-op -- reason: a non-string kind is invalid input the API refuses with its own error, so the body goes as given.
		return
	}
	if kind == nil || strings.EqualFold(*kind, current.Kind) {
		return
	}
	var flag string
	switch strings.ToUpper(*kind) {
	case kindPerson:
		flag = fieldMainBusinessContact
	case kindBusiness:
		flag = fieldMainPersonContact
	default:
		return
	}
	if _, ok := body[flag]; !ok {
		return
	}
	if _, ok := given[flag]; ok {
		return
	}
	body[flag] = json.RawMessage(`false`)
}
