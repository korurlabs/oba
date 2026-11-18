package find

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// LeadEnums are the values the API allows for a lead's country, stage and size, read from its
// create schema. The search limits Claude Code to them and the creates send no other value.
type LeadEnums struct {
	Countries []string
	Stages    []string
	Sizes     []string
}

// LeadFields reads, from the create schema of the API at apiURL, the country codes, the stages
// and the sizes a lead may have. All three lists must be there: the search and the creates rely
// on them.
func LeadFields(apiURL string) (LeadEnums, error) {
	doc, err := apidocs.FetchDoc(apiURL)
	if err != nil {
		return LeadEnums{}, err
	}
	fields, err := doc.Fields(createSchema)
	if err != nil {
		return LeadEnums{}, apidocs.FieldListError(apiURL, err)
	}
	enums := LeadEnums{
		Countries: enumOf(fields, fieldCountry),
		Stages:    enumOf(fields, fieldStage),
		Sizes:     enumOf(fields, fieldSize),
	}
	if len(enums.Countries) == 0 || len(enums.Stages) == 0 {
		return LeadEnums{}, apidocs.FieldListError(apiURL, fmt.Errorf("schema %q lists no country codes or no stages", createSchema))
	}
	if len(enums.Sizes) == 0 {
		return LeadEnums{}, apidocs.FieldListError(apiURL, fmt.Errorf("schema %q lists no company sizes", createSchema))
	}
	return enums, nil
}

// enumOf returns the allowed values of the named field, or nil when the field is missing.
func enumOf(fields []apidocs.Field, name string) []string {
	for _, field := range fields {
		if field.Name == name {
			return field.Enum
		}
	}
	return nil
}

// allowedValues are the live enum values as sets, for the checks of leadRequest.
type allowedValues struct {
	countries map[string]bool
	sizes     map[string]bool
}

func newAllowedValues(enums LeadEnums) allowedValues {
	return allowedValues{countries: setOf(enums.Countries), sizes: setOf(enums.Sizes)}
}

func setOf(values []string) map[string]bool {
	set := make(map[string]bool, len(values))
	for _, value := range values {
		set[value] = true
	}
	return set
}
