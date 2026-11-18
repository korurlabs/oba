package org

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func profile(t *testing.T, data string) client.OrganizationProfile {
	t.Helper()
	var current client.OrganizationProfile
	if err := json.Unmarshal([]byte(data), &current); err != nil {
		t.Fatal(err)
	}
	return current
}

func TestMergeUpdateLaysGivenKeysOverCurrent(t *testing.T) {
	current := profile(t, `{"id":"o","name":"Korur B.V.","description":"Security  firm","website":"https://korur.com/","status":"ACTIVE"}`)
	fields := []apidocs.Field{{Name: "name"}, {Name: "description"}, {Name: "website"}}
	body, err := mergeUpdate(current, map[string]json.RawMessage{"website": json.RawMessage(`null`)}, fields)
	if err != nil {
		t.Fatal(err)
	}
	if string(body["name"]) != `"Korur B.V."` || string(body["description"]) != `"Security  firm"` || string(body["website"]) != "null" || len(body) != 3 {
		t.Errorf("body = %v", body)
	}
}

func TestMergeUpdateRefusesFieldTheOrganizationLacks(t *testing.T) {
	current := profile(t, `{"id":"o","name":"Korur B.V."}`)
	_, err := mergeUpdate(current, map[string]json.RawMessage{}, []apidocs.Field{{Name: "name"}, {Name: "industry"}})
	if err == nil || !strings.Contains(err.Error(), `cannot keep field "industry"`) {
		t.Fatalf("err = %v", err)
	}
}

func TestChangesComparesDecodedJSON(t *testing.T) {
	current := profile(t, `{"id":"o","name":"Korur B.V.","website":null}`)
	if changes(current, map[string]json.RawMessage{"name": json.RawMessage(` "Korur B.V." `), "website": json.RawMessage(`null`)}) {
		t.Error("equal values counted as a change")
	}
	if !changes(current, map[string]json.RawMessage{"name": json.RawMessage(`"Korur"`)}) {
		t.Error("different value not counted")
	}
	if !changes(current, map[string]json.RawMessage{"industry": json.RawMessage(`null`)}) {
		t.Error("absent key not counted")
	}
}
