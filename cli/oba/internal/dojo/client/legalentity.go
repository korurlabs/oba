package client

import (
	"fmt"
	"net/url"
)

// legalEntitiesPath is the API path of the current organization's legal entities: the companies
// in whose name it issues documents.
const legalEntitiesPath = "/legal-entities"

// LegalEntity is one legal entity of the organization. The list (LegalEntityListResponse)
// returns the fields up to RegistrationNumber and UpdatedAt; the detail
// (LegalEntityDetailResponse), which get, create and update return, adds the others. Optional
// fields are nil when the entity leaves them empty. UpdatedAt and CreatedAt are UTC instants.
type LegalEntity struct {
	ID                 string  `json:"id"`
	ShortName          string  `json:"shortName"`
	LegalName          string  `json:"legalName"`
	Country            string  `json:"country"`
	RegistrationNumber *string `json:"registrationNumber"`
	VATNumber          *string `json:"vatNumber"`
	Street             *string `json:"street"`
	PostalCode         *string `json:"postalCode"`
	City               *string `json:"city"`
	GoverningLaw       *string `json:"governingLaw"`
	Court              *string `json:"court"`
	CreatedAt          string  `json:"createdAt,omitempty"`
	UpdatedAt          string  `json:"updatedAt,omitempty"`
}

// LegalEntityRequest is the body of both POST /legal-entities and PUT /legal-entities/{id}. The
// PUT replaces every field, so a nil optional field is sent as null and clears it.
type LegalEntityRequest struct {
	ShortName          string  `json:"shortName"`
	LegalName          string  `json:"legalName"`
	Country            string  `json:"country"`
	RegistrationNumber *string `json:"registrationNumber"`
	VATNumber          *string `json:"vatNumber"`
	Street             *string `json:"street"`
	PostalCode         *string `json:"postalCode"`
	City               *string `json:"city"`
	GoverningLaw       *string `json:"governingLaw"`
	Court              *string `json:"court"`
}

func legalEntityPath(id string) string {
	return legalEntitiesPath + "/" + url.PathEscape(id)
}

// ListLegalEntities reads every legal entity of the organization, sorted by short name. The
// endpoint is not paged.
func (c *Client) ListLegalEntities() ([]LegalEntity, error) {
	body, err := c.Get(legalEntitiesPath)
	if err != nil {
		return nil, err
	}
	return parseData[[]LegalEntity](body, "the legal entities")
}

// GetLegalEntity reads legal entity id with every field.
func (c *Client) GetLegalEntity(id string) (LegalEntity, error) {
	body, err := c.Get(legalEntityPath(id))
	if err != nil {
		return LegalEntity{}, err
	}
	return parseData[LegalEntity](body, "legal entity "+id)
}

// CreateLegalEntity creates a legal entity and returns it as stored.
func (c *Client) CreateLegalEntity(req LegalEntityRequest) (LegalEntity, error) {
	body, err := c.Post(legalEntitiesPath, req)
	if err != nil {
		return LegalEntity{}, err
	}
	created, err := parseData[LegalEntity](body, "the created legal entity")
	if err != nil {
		return LegalEntity{}, fmt.Errorf("the legal entity was created, but the response could not be read: %w", err)
	}
	return created, nil
}

// UpdateLegalEntity replaces every field of legal entity id and returns it as stored.
func (c *Client) UpdateLegalEntity(id string, req LegalEntityRequest) (LegalEntity, error) {
	body, err := c.Put(legalEntityPath(id), req)
	if err != nil {
		return LegalEntity{}, err
	}
	updated, err := parseData[LegalEntity](body, "the updated legal entity")
	if err != nil {
		return LegalEntity{}, fmt.Errorf("legal entity %s was updated, but the response could not be read: %w", id, err)
	}
	return updated, nil
}

// DeleteLegalEntity deletes legal entity id.
func (c *Client) DeleteLegalEntity(id string) error {
	_, err := c.Delete(legalEntityPath(id))
	return err
}
