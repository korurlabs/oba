package content

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// Fields are the fields of the request schema, in the order of the backend record.
func Fields(doc *apidocs.Doc) ([]apidocs.Field, error) {
	return doc.Fields(requestSchema)
}

// LoadFields reads the request fields from the API at apiURL; a failure is a FieldListError.
func LoadFields(apiURL string) ([]apidocs.Field, error) {
	doc, err := apidocs.FetchDoc(apiURL)
	if err != nil {
		return nil, err
	}
	fields, err := Fields(doc)
	if err != nil {
		return nil, apidocs.FieldListError(apiURL, err)
	}
	return fields, nil
}

// FieldSection is the help section that lists the request fields from the API at apiURL as a
// template, the required ones marked when markRequired is set. Without the API it gives the
// reason instead, so that help still prints.
func FieldSection(apiURL string, markRequired bool) string {
	fields, err := LoadFields(apiURL)
	if err != nil {
		return fmt.Sprintf("Fields unavailable: %v.", err)
	}
	heading := fmt.Sprintf("Fields, from %s%s:", strings.TrimRight(apiURL, "/"), apidocs.Path)
	if !markRequired {
		heading += " give only the ones to change."
	}
	return heading + "\n\n" + apidocs.Template(fields, markRequired)
}
