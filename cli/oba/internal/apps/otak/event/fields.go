package event

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// FieldSource lists the fields a command accepts from the API document.
type FieldSource func(doc *apidocs.Doc) ([]apidocs.Field, error)

// CreateFields are the fields of the create schema, in the order of the backend record.
func CreateFields(doc *apidocs.Doc) ([]apidocs.Field, error) {
	return doc.Fields(createSchema)
}

// UpdateFields are the fields of the update schema, in the order of the backend record.
func UpdateFields(doc *apidocs.Doc) ([]apidocs.Field, error) {
	return doc.Fields(updateSchema)
}

// LoadFields reads the fields of source from the API at apiURL; a failure is a FieldListError.
func LoadFields(apiURL string, source FieldSource) ([]apidocs.Field, error) {
	doc, err := apidocs.FetchDoc(apiURL)
	if err != nil {
		return nil, err
	}
	fields, err := source(doc)
	if err != nil {
		return nil, apidocs.FieldListError(apiURL, err)
	}
	return fields, nil
}

// FieldSection is the help section that lists the fields of source from the API at apiURL as a
// template. Without the API it gives the reason instead, so that help still prints.
func FieldSection(apiURL string, source FieldSource, markRequired bool) string {
	fields, err := LoadFields(apiURL, source)
	if err != nil {
		return fmt.Sprintf("Fields unavailable: %v.", err)
	}
	heading := fmt.Sprintf("Fields, from %s%s:", strings.TrimRight(apiURL, "/"), apidocs.Path)
	if !markRequired {
		heading += " give only the ones to change."
	}
	return heading + "\n\n" + apidocs.Template(fields, markRequired)
}
