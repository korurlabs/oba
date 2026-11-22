package legalentity

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// createRequest builds the POST body and refuses a missing short name, legal name or country,
// before any call. Optional fields that were not given, or given blank, are sent as null.
func (in Input) createRequest() (client.LegalEntityRequest, error) {
	for _, r := range in.required() {
		if r.value == nil || strings.TrimSpace(*r.value) == "" {
			return client.LegalEntityRequest{}, fmt.Errorf("%s is required", r.flag)
		}
	}
	return client.LegalEntityRequest{
		ShortName:          *in.ShortName,
		LegalName:          *in.LegalName,
		Country:            *in.Country,
		RegistrationNumber: optional(in.RegistrationNumber),
		VATNumber:          optional(in.VATNumber),
		Street:             optional(in.Street),
		PostalCode:         optional(in.PostalCode),
		City:               optional(in.City),
		GoverningLaw:       optional(in.GoverningLaw),
		Court:              optional(in.Court),
	}, nil
}

// CheckCreate refuses missing required values without a call.
func (in Input) CheckCreate() error {
	_, err := in.createRequest()
	return err
}

// Create creates a legal entity in the client's organization and returns it as stored.
func Create(c *client.Client, in Input) (client.LegalEntity, error) {
	req, err := in.createRequest()
	if err != nil {
		return client.LegalEntity{}, err
	}
	return c.CreateLegalEntity(req)
}
