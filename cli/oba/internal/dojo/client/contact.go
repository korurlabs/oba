package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// Contact is one contact person of an Otak relation as the contact list returns it
// (ContactListResponse) and a create or update returns it (ContactDetailResponse). Kind is
// PERSON (a person at the relation) or BUSINESS (the company's general details, such as info@).
// MainPersonContact marks the one person of the relation to talk to first, MainBusinessContact
// the one business contact whose address the relation list shows; each fits only its own kind.
// Optional fields are nil when the contact leaves them empty. Values keeps every property as the
// API sent it, so that an update writes back the unchanged fields byte for byte, since the API
// replaces them all.
type Contact struct {
	ID                  string                     `json:"id"`
	RelationID          string                     `json:"relationId"`
	Name                string                     `json:"name"`
	Kind                string                     `json:"kind"`
	Role                *string                    `json:"role"`
	Email               *string                    `json:"email"`
	EmailType           *string                    `json:"emailType"`
	Phone               *string                    `json:"phone"`
	LinkedinURL         *string                    `json:"linkedinUrl"`
	DecisionMaker       bool                       `json:"decisionMaker"`
	MainPersonContact   bool                       `json:"mainPersonContact"`
	MainBusinessContact bool                       `json:"mainBusinessContact"`
	SourceURL           *string                    `json:"sourceUrl"`
	Values              map[string]json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (ct *Contact) UnmarshalJSON(data []byte) error {
	type plain Contact
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	if err := json.Unmarshal(data, &typed.Values); err != nil {
		return err
	}
	*ct = Contact(typed)
	return nil
}

// ListContacts reads every contact of relation relationID, in the API's order: the main person
// contact first, then the main business contact, then by name. The endpoint is not paged.
func (c *Client) ListContacts(relationID string) ([]Contact, error) {
	body, err := c.Get(contactsPath(relationID))
	if err != nil {
		return nil, err
	}
	var resp APIResponse[[]Contact]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to read the contacts of relation %s: %w", relationID, err)
	}
	return resp.Data, nil
}

// CreateContact sends a ContactCreateRequest body and returns the created contact.
func (c *Client) CreateContact(relationID string, body any) (Contact, error) {
	resp, err := c.Post(contactsPath(relationID), body)
	if err != nil {
		return Contact{}, err
	}
	contact, err := parseContact(resp)
	if err != nil {
		return Contact{}, fmt.Errorf("the contact was created, but the response could not be read: %w", err)
	}
	return contact, nil
}

// UpdateContact replaces every field of contact contactID with a ContactUpdateRequest body and
// returns the result.
func (c *Client) UpdateContact(relationID, contactID string, body any) (Contact, error) {
	resp, err := c.Put(contactPath(relationID, contactID), body)
	if err != nil {
		return Contact{}, err
	}
	contact, err := parseContact(resp)
	if err != nil {
		return Contact{}, fmt.Errorf("contact %s was updated, but the response could not be read: %w", contactID, err)
	}
	return contact, nil
}

// DeleteContact deletes contact contactID of relation relationID.
func (c *Client) DeleteContact(relationID, contactID string) error {
	_, err := c.Delete(contactPath(relationID, contactID))
	return err
}

func contactsPath(relationID string) string {
	return "/otak/relations/" + url.PathEscape(relationID) + "/contacts"
}

func contactPath(relationID, contactID string) string {
	return contactsPath(relationID) + "/" + url.PathEscape(contactID)
}

func parseContact(body []byte) (Contact, error) {
	var resp APIResponse[Contact]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Contact{}, err
	}
	return resp.Data, nil
}
