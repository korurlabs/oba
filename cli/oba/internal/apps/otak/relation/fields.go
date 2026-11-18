package relation

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// FieldSource lists the fields a command accepts from the API document.
type FieldSource func(doc *apidocs.Doc) ([]apidocs.Field, error)

// CreateFields are the fields of the create schema.
func CreateFields(doc *apidocs.Doc) ([]apidocs.Field, error) {
	return doc.Fields(createSchema)
}

// UpdateFields is the update schema plus type and stage, which change through their own calls
// and are described by the create schema.
func UpdateFields(doc *apidocs.Doc) ([]apidocs.Field, error) {
	fields, err := doc.Fields(updateSchema)
	if err != nil {
		return nil, err
	}
	createFields, err := doc.Fields(createSchema)
	if err != nil {
		return nil, err
	}
	extra := make([]apidocs.Field, 0, 2)
	for _, name := range []string{fieldType, fieldStage} {
		found := false
		for _, field := range createFields {
			if field.Name == name {
				field.Required = false
				extra = append(extra, field)
				found = true
				break
			}
		}
		if !found {
			return nil, fmt.Errorf("the document has no property %q in schema %q", name, createSchema)
		}
	}
	return append(extra, fields...), nil
}

func loadFields(apiURL string, source FieldSource) ([]apidocs.Field, error) {
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

// FieldSection is the help section that lists the fields of the API at apiURL as a template.
// Without the API it gives the reason instead, so that help still prints.
func FieldSection(apiURL string, source FieldSource, markRequired bool) string {
	fields, err := loadFields(apiURL, source)
	if err != nil {
		return fmt.Sprintf("Fields unavailable: %v.", err)
	}
	heading := fmt.Sprintf("Fields, from %s%s:", strings.TrimRight(apiURL, "/"), apidocs.Path)
	if !markRequired {
		heading += " give only the ones to change."
	}
	return heading + "\n\n" + apidocs.Template(fields, markRequired)
}
