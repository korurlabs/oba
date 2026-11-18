package apidocs

import (
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
)

const doc = `{"components":{"schemas":{
  "Status": {"type":"string","enum":["A","B"]},
  "Req": {
    "type":"object",
    "required":["zeta","status"],
    "properties":{
      "zeta":   {"type":"string"},
      "status": {"$ref":"#/components/schemas/Status"},
      "kind":   {"type":"string","enum":["X","Y","Z"]},
      "amount": {"type":"number"},
      "owner":  {"type":"string","format":"uuid"},
      "legacy": {"type":"string","nullable":true},
      "modern": {"type":["string","null"]},
      "anyof":  {"anyOf":[{"type":"integer"},{"type":"null"}]},
      "wrapped":{"allOf":[{"$ref":"#/components/schemas/Status"}],"nullable":true},
      "alpha":  {"type":"boolean"},
      "nulled": {"type":["string","null"],"enum":["P","Q",null]}
    }
  }
}}}`

func fieldsByName(t *testing.T) (map[string]Field, []string) {
	t.Helper()
	d, err := Parse([]byte(doc))
	if err != nil {
		t.Fatal(err)
	}
	fields, err := d.Fields("Req")
	if err != nil {
		t.Fatal(err)
	}
	byName := map[string]Field{}
	var order []string
	for _, f := range fields {
		byName[f.Name] = f
		order = append(order, f.Name)
	}
	return byName, order
}

func TestFieldsOrderRequiredAndTypes(t *testing.T) {
	f, order := fieldsByName(t)
	wantOrder := []string{"zeta", "status", "kind", "amount", "owner", "legacy", "modern", "anyof", "wrapped", "alpha", "nulled"}
	if !reflect.DeepEqual(order, wantOrder) {
		t.Errorf("order = %v, want %v", order, wantOrder)
	}
	if !f["zeta"].Required || !f["status"].Required || f["kind"].Required {
		t.Errorf("required flags wrong: %+v", f)
	}
	if f["owner"].Type != "string (uuid)" || f["amount"].Type != "number" {
		t.Errorf("types: %+v %+v", f["owner"], f["amount"])
	}
}

func TestFieldsEnums(t *testing.T) {
	f, _ := fieldsByName(t)
	if !reflect.DeepEqual(f["kind"].Enum, []string{"X", "Y", "Z"}) {
		t.Errorf("inline enum = %v", f["kind"].Enum)
	}
	if !reflect.DeepEqual(f["status"].Enum, []string{"A", "B"}) || f["status"].Type != "string" {
		t.Errorf("$ref enum = %+v", f["status"])
	}
	if !reflect.DeepEqual(f["wrapped"].Enum, []string{"A", "B"}) {
		t.Errorf("allOf enum = %+v", f["wrapped"])
	}
	if !reflect.DeepEqual(f["nulled"].Enum, []string{"P", "Q"}) || !f["nulled"].Nullable {
		t.Errorf("null in enum = %+v", f["nulled"])
	}
}

func TestFieldsNullableForms(t *testing.T) {
	f, _ := fieldsByName(t)
	for _, name := range []string{"legacy", "modern", "anyof", "wrapped"} {
		if !f[name].Nullable {
			t.Errorf("%s should be nullable: %+v", name, f[name])
		}
	}
	if f["modern"].Type != "string" || f["anyof"].Type != "integer" {
		t.Errorf("types: %+v %+v", f["modern"], f["anyof"])
	}
	for _, name := range []string{"zeta", "kind", "alpha", "status"} {
		if f[name].Nullable {
			t.Errorf("%s should not be nullable", name)
		}
	}
}

func TestFieldsErrors(t *testing.T) {
	tests := []struct{ name, doc, schema, want string }{
		{"missing schema", doc, "Nope", `no schema "Nope"`},
		{"no properties", `{"components":{"schemas":{"E":{"type":"object"}}}}`, "E", "has no properties"},
		{"bad ref", `{"components":{"schemas":{"S":{"properties":{"a":{"$ref":"#/components/schemas/Gone"}}}}}}`, "S", "unresolved reference"},
		{"ref cycle", `{"components":{"schemas":{"S":{"properties":{"a":{"$ref":"#/components/schemas/C"}}},"C":{"$ref":"#/components/schemas/C"}}}}`, "S", "nest too deep"},
	}
	for _, tt := range tests {
		d, err := Parse([]byte(tt.doc))
		if err != nil {
			t.Fatal(err)
		}
		if _, err := d.Fields(tt.schema); err == nil || !strings.Contains(err.Error(), tt.want) {
			t.Errorf("%s: err = %v, want %q", tt.name, err, tt.want)
		}
	}
}

func TestParseErrors(t *testing.T) {
	if _, err := Parse([]byte("nope")); err == nil {
		t.Error("invalid JSON accepted")
	}
	if _, err := Parse([]byte(`{"components":{}}`)); err == nil {
		t.Error("document without schemas accepted")
	}
}

func TestSuggest(t *testing.T) {
	names := []string{"name", "country", "stage", "size", "source"}
	tests := []struct{ key, want string }{
		{"contry", "country"},
		{"STAGE", "stage"},
		{"stge", "stage"},
		{"zzzzzzzz", ""},
		{"nam", "name"},
	}
	for _, tt := range tests {
		if got := Suggest(tt.key, names); got != tt.want {
			t.Errorf("Suggest(%q) = %q, want %q", tt.key, got, tt.want)
		}
	}
	// distance 1 to both; the first listed wins
	if got := Suggest("sizt", []string{"size", "sizz"}); got != "size" {
		t.Errorf("tie = %q", got)
	}
}

func TestFetch(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != Path || r.Header.Get("Authorization") != "" {
			t.Errorf("unexpected request %s auth=%q", r.URL.Path, r.Header.Get("Authorization"))
		}
		_, _ = w.Write([]byte(doc))
	}))
	defer srv.Close()
	d, err := Fetch(srv.URL + "/")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := d.Fields("Req"); err != nil {
		t.Error(err)
	}

	bad := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "x", http.StatusNotFound)
	}))
	defer bad.Close()
	if _, err := Fetch(bad.URL); err == nil || !strings.Contains(err.Error(), "404") {
		t.Errorf("err = %v", err)
	}
}
