// Package legalentity lists, reads, creates, changes and deletes the legal entities of the
// current organization: the companies in whose name it issues documents, each with the
// registration details and the governing law and court its contracts name.
package legalentity

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// Input holds the field values a command was given, one per flag. A nil field was not given. A
// blank optional field is sent as null, which leaves it empty on create and clears it on update;
// the API refuses a blank string there.
type Input struct {
	ShortName          *string
	LegalName          *string
	Country            *string
	RegistrationNumber *string
	VATNumber          *string
	Street             *string
	PostalCode         *string
	City               *string
	GoverningLaw       *string
	Court              *string
}

// empty reports whether no field was given.
func (in Input) empty() bool {
	for _, value := range []*string{in.ShortName, in.LegalName, in.Country, in.RegistrationNumber, in.VATNumber,
		in.Street, in.PostalCode, in.City, in.GoverningLaw, in.Court} {
		if value != nil {
			return false
		}
	}
	return true
}

// requiredField is a field the API refuses empty, with the flag that sets it.
type requiredField struct {
	flag  string
	value *string
}

// required lists the short name, legal name and country, the fields every legal entity has.
func (in Input) required() []requiredField {
	return []requiredField{{"--short-name", in.ShortName}, {"--legal-name", in.LegalName}, {"--country", in.Country}}
}

// ParseID checks a legal entity id argument before any request: legal entities are named by
// their id only.
func ParseID(arg string) (string, error) {
	id := strings.TrimSpace(arg)
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid legal entity ID %q: use the ID that 'oba legal-entity list' prints", arg)
	}
	return id, nil
}

// Label names a legal entity in command output by its short name and id.
func Label(e client.LegalEntity) string {
	if e.ShortName == "" {
		return e.ID
	}
	return fmt.Sprintf("%q (%s)", guardrail.OneLine(e.ShortName), e.ID)
}

// optional is the value of a given optional field: nil when it is blank, so that the API stores
// no value.
func optional(value *string) *string {
	if value == nil || strings.TrimSpace(*value) == "" {
		return nil
	}
	return value
}

// orDash is an optional value on one terminal-safe line, or "-" when it is empty.
func orDash(value *string) string {
	if value == nil || *value == "" {
		return "-"
	}
	return guardrail.OneLine(*value)
}
