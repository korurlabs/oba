package find

import (
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
)

func TestLeadFields(t *testing.T) {
	doc := func(withSize bool) string {
		size := ""
		if withSize {
			size = `"size":{"type":"string","enum":["SIZE_1_10","SIZE_11_50"]},`
		}
		return `{"openapi":"3.1.0","paths":{},"components":{"schemas":{
			"Country":{"type":"string","enum":["NL","DE"]},
			"RelationCreateRequest":{"type":"object","properties":{
				"country":{"$ref":"#/components/schemas/Country"},
				"stage":{"type":"string","enum":["NEW","LOST"]},` + size + `
				"name":{"type":"string"}}}}}}`
	}
	serve := func(body string) *httptest.Server {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(body))
		}))
		t.Cleanup(srv.Close)
		return srv
	}
	t.Run("reads country, stage and size", func(t *testing.T) {
		got, err := LeadFields(serve(doc(true)).URL)
		if err != nil {
			t.Fatal(err)
		}
		want := LeadEnums{Countries: []string{"NL", "DE"}, Stages: []string{"NEW", "LOST"}, Sizes: []string{"SIZE_1_10", "SIZE_11_50"}}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("got %+v, want %+v", got, want)
		}
	})
	t.Run("errors without size", func(t *testing.T) {
		_, err := LeadFields(serve(doc(false)).URL)
		if err == nil || !strings.Contains(err.Error(), "lists no company sizes") {
			t.Fatalf("err = %v", err)
		}
	})
}
