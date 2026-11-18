package apidocs

import (
	"encoding/json"
	"reflect"
	"testing"
)

func TestSchemaFromDocument(t *testing.T) {
	d, err := Parse([]byte(`{"components":{"schemas":{
	  "Size": {"type":"string","enum":["S","M"]},
	  "Req": {"type":"object","required":["name","kind"],"properties":{
	    "name":  {"type":"string","maxLength":100},
	    "kind":  {"type":"string","enum":["A","B"]},
	    "size":  {"anyOf":[{"$ref":"#/components/schemas/Size"},{"type":"null"}]},
	    "owner": {"type":"string","format":"uuid","nullable":true},
	    "count": {"type":"integer","enum":[1,2]},
	    "free":  {}
	  }}
	}}}`))
	if err != nil {
		t.Fatal(err)
	}
	fields, err := d.Fields("Req")
	if err != nil {
		t.Fatal(err)
	}
	text, err := Schema(fields)
	if err != nil {
		t.Fatal(err)
	}
	var got map[string]any
	if err := json.Unmarshal([]byte(text), &got); err != nil {
		t.Fatal(err)
	}
	want := map[string]any{
		"type":                 "object",
		"additionalProperties": false,
		"required":             []any{"name", "kind"},
		"properties": map[string]any{
			"name":  map[string]any{"type": "string", "maxLength": float64(100)},
			"kind":  map[string]any{"type": "string", "enum": []any{"A", "B"}},
			"size":  map[string]any{"type": []any{"string", "null"}, "enum": []any{"S", "M", nil}},
			"owner": map[string]any{"type": []any{"string", "null"}},
			"count": map[string]any{"type": "integer", "enum": []any{float64(1), float64(2)}},
			"free":  map[string]any{},
		},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("schema =\n%s\nwant\n%v", text, want)
	}
}

func TestSchemaNoRequired(t *testing.T) {
	text, err := Schema([]Field{{Name: "a", Type: "string"}})
	if err != nil {
		t.Fatal(err)
	}
	// An empty list, not null: a schema validator refuses "required": null.
	if want := `{"additionalProperties":false,"properties":{"a":{"type":"string"}},"required":[],"type":"object"}`; text != want {
		t.Errorf("schema = %s, want %s", text, want)
	}
}
