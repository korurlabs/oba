// Package contact lists, creates, updates and deletes the contact persons of an Otak relation.
// A create or update takes a JSON object checked against the live ContactCreateRequest or
// ContactUpdateRequest schema. It also holds the contacts Claude Code finds on the web (the
// found_ files), which oba otak find and oba otak contact fill share, and the fill flow for
// existing relations (the fill_ files).
package contact

import (
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	// relationsPath is the API path of the relations; a relation's contacts are below it.
	relationsPath = "/otak/relations"

	// createSchema is the body of a create. updateSchema is the body of an update, which
	// replaces every field; it has no source URL.
	createSchema = "ContactCreateRequest"
	updateSchema = "ContactUpdateRequest"

	// fieldSourceURL is the page the contact was found on. It is provenance the person is told
	// about at first contact, so the API keeps it on update and the CLI refuses to change it.
	fieldSourceURL = "sourceUrl"

	// fieldEmail and fieldEmailType are the address and its kind (COMPANY, SHARED, PERSONAL).
	// The API refuses a kind without an address.
	fieldEmail     = "email"
	fieldEmailType = "emailType"

	// fieldKind is PERSON or BUSINESS. Each kind has its own main flag, and the API refuses a
	// true flag of the other kind.
	fieldKind                = "kind"
	fieldMainPersonContact   = "mainPersonContact"
	fieldMainBusinessContact = "mainBusinessContact"

	// kindPerson and kindBusiness are the values of fieldKind.
	kindPerson   = "PERSON"
	kindBusiness = "BUSINESS"
)

func optional(value *string) string {
	if value == nil {
		return "-"
	}
	return orDash(guardrail.OneLine(*value))
}

func orDash(value string) string {
	if value == "" {
		return "-"
	}
	return value
}

// orEmpty is the value of an optional found field, or "" when it is nil.
func orEmpty(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}
