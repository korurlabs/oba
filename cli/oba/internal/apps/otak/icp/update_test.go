package icp

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

var testFields = []apidocs.Field{
	{Name: "name", Type: "string", Required: true},
	{Name: "industry", Type: "string", Enum: []string{"SOFTWARE", "OTHER"}, Required: true},
	{Name: "country", Type: "string", Nullable: true},
	{Name: "description", Type: "string", Nullable: true},
}

func TestMergeUpdate(t *testing.T) {
	var current client.Icp
	if err := json.Unmarshal([]byte(`{"id":"i1","name":"A","industry":"SOFTWARE","country":"NL","description":null,"updatedAt":"x"}`), &current); err != nil {
		t.Fatal(err)
	}
	if current.Name != "A" || string(current.Values["updatedAt"]) != `"x"` {
		t.Fatalf("decoded = %+v", current)
	}
	body, err := mergeUpdate(current, map[string]json.RawMessage{"country": json.RawMessage("null"), "name": json.RawMessage(`"B"`)}, testFields)
	if err != nil {
		t.Fatal(err)
	}
	got, err := json.Marshal(body)
	if err != nil {
		t.Fatal(err)
	}
	if want := `{"country":null,"description":null,"industry":"SOFTWARE","name":"B"}`; string(got) != want {
		t.Errorf("body = %s, want %s", got, want)
	}

	delete(current.Values, "description")
	if _, err := mergeUpdate(current, map[string]json.RawMessage{"name": json.RawMessage(`"B"`)}, testFields); err == nil ||
		!strings.Contains(err.Error(), `cannot keep field "description"`) {
		t.Errorf("err = %v", err)
	}
}
