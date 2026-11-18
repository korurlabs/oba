package contact

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// fieldsDoc has a create and an update schema with one field each, named after the schema, so
// that a test sees which schema a field source read.
const fieldsDoc = `{"openapi":"3.1.0","components":{"schemas":{
  "ContactCreateRequest":{"type":"object","properties":{"fromCreate":{"type":"string"}}},
  "ContactUpdateRequest":{"type":"object","properties":{"fromUpdate":{"type":"string"}}}}}}`

func TestFieldSourcesReadTheirSchema(t *testing.T) {
	doc, err := apidocs.Parse([]byte(fieldsDoc))
	if err != nil {
		t.Fatal(err)
	}
	for _, tt := range []struct {
		name   string
		source FieldSource
		want   string
	}{
		{"create", CreateFields, "fromCreate"},
		{"update", UpdateFields, "fromUpdate"},
	} {
		fields, err := tt.source(doc)
		if err != nil {
			t.Fatalf("%s: %v", tt.name, err)
		}
		if len(fields) != 1 || fields[0].Name != tt.want {
			t.Errorf("%s fields = %+v, want only %s", tt.name, fields, tt.want)
		}
	}
}
