package legalentity

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// CheckUpdate refuses an update with no field (terminal.ErrNothingToUpdate) or a blank required
// field, before any call: a short name, legal name and country can be changed but not cleared.
func (in Input) CheckUpdate() error {
	if in.empty() {
		return terminal.ErrNothingToUpdate
	}
	for _, r := range in.required() {
		if r.value != nil && strings.TrimSpace(*r.value) == "" {
			return fmt.Errorf("%s cannot be empty", r.flag)
		}
	}
	return nil
}

// Update changes only the given fields of legal entity id. The API replaces every field and a
// null clears it, so Update reads the entity first and sends its stored values for every field
// that was not given.
func Update(c *client.Client, id string, in Input) (client.LegalEntity, error) {
	if err := in.CheckUpdate(); err != nil {
		return client.LegalEntity{}, err
	}
	current, err := c.Unrecorded().GetLegalEntity(id)
	if err != nil {
		return client.LegalEntity{}, err
	}
	return c.UpdateLegalEntity(id, merge(current, in))
}

// merge is the PUT body: the stored values of current with the given fields of in laid over
// them. A given blank optional field becomes null.
func merge(current client.LegalEntity, in Input) client.LegalEntityRequest {
	req := client.LegalEntityRequest{
		ShortName:          current.ShortName,
		LegalName:          current.LegalName,
		Country:            current.Country,
		RegistrationNumber: current.RegistrationNumber,
		VATNumber:          current.VATNumber,
		Street:             current.Street,
		PostalCode:         current.PostalCode,
		City:               current.City,
		GoverningLaw:       current.GoverningLaw,
		Court:              current.Court,
	}
	if in.ShortName != nil {
		req.ShortName = *in.ShortName
	}
	if in.LegalName != nil {
		req.LegalName = *in.LegalName
	}
	if in.Country != nil {
		req.Country = *in.Country
	}
	overlay := []struct {
		given  *string
		target **string
	}{
		{in.RegistrationNumber, &req.RegistrationNumber},
		{in.VATNumber, &req.VATNumber},
		{in.Street, &req.Street},
		{in.PostalCode, &req.PostalCode},
		{in.City, &req.City},
		{in.GoverningLaw, &req.GoverningLaw},
		{in.Court, &req.Court},
	}
	for _, o := range overlay {
		if o.given != nil {
			*o.target = optional(o.given)
		}
	}
	return req
}
