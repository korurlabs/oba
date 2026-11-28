package content

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

const fieldsDoc = `{"openapi":"3.1.0","components":{"schemas":{
  "CampaignCreateRequest":{"type":"object","properties":{"name":{"type":"string"}}},
  "CampaignContentRequest":{"type":"object","properties":{"text":{"type":"string"}}}}}}`

func TestFieldsReadTheContentSchema(t *testing.T) {
	doc, err := apidocs.Parse([]byte(fieldsDoc))
	if err != nil {
		t.Fatal(err)
	}
	fields, err := Fields(doc)
	if err != nil {
		t.Fatal(err)
	}
	if len(fields) != 1 || fields[0].Name != "text" {
		t.Errorf("fields = %+v, want only text", fields)
	}
}
