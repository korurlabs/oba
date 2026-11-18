package contact

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestMergeUpdateLaysGivenKeysOverCurrent(t *testing.T) {
	var current client.Contact
	if err := json.Unmarshal([]byte(`{"id":"c","name":"Jan","role":"Director","phone":null,"sourceUrl":"https://a.nl"}`), &current); err != nil {
		t.Fatal(err)
	}
	fields := []apidocs.Field{{Name: "name"}, {Name: "role"}, {Name: "phone"}}
	body, err := mergeUpdate(current, map[string]json.RawMessage{"role": json.RawMessage(`null`)}, fields)
	if err != nil {
		t.Fatal(err)
	}
	if string(body["name"]) != `"Jan"` || string(body["role"]) != "null" || string(body["phone"]) != "null" || len(body) != 3 {
		t.Errorf("body = %v", body)
	}
}

func TestMergeUpdateRefusesFieldTheContactLacks(t *testing.T) {
	var current client.Contact
	if err := json.Unmarshal([]byte(`{"id":"c","name":"Jan"}`), &current); err != nil {
		t.Fatal(err)
	}
	_, err := mergeUpdate(current, map[string]json.RawMessage{}, []apidocs.Field{{Name: "name"}, {Name: "email"}})
	if err == nil || !strings.Contains(err.Error(), `cannot keep field "email"`) {
		t.Fatalf("err = %v", err)
	}
}

func TestChangesComparesDecodedJSON(t *testing.T) {
	var current client.Contact
	if err := json.Unmarshal([]byte(`{"id":"c","name":"Jan","decisionMaker":false}`), &current); err != nil {
		t.Fatal(err)
	}
	if changes(current, map[string]json.RawMessage{"name": json.RawMessage(` "Jan" `), "decisionMaker": json.RawMessage(`false`)}) {
		t.Error("equal values counted as a change")
	}
	if !changes(current, map[string]json.RawMessage{"name": json.RawMessage(`"Jon"`)}) {
		t.Error("different value not counted")
	}
	if !changes(current, map[string]json.RawMessage{"role": json.RawMessage(`null`)}) {
		t.Error("absent key not counted")
	}
}

func TestMergeUpdateClearsEmailTypeWithEmail(t *testing.T) {
	var current client.Contact
	if err := json.Unmarshal([]byte(`{"id":"c","name":"Jan","email":"jan@acme.nl","emailType":"COMPANY"}`), &current); err != nil {
		t.Fatal(err)
	}
	fields := []apidocs.Field{{Name: "name"}, {Name: "email"}, {Name: "emailType"}}
	tests := []struct {
		name  string
		given string
		want  string
	}{
		{"email null", `{"email":null}`, "null"},
		{"email blank", `{"email":"  "}`, "null"},
		{"email changed", `{"email":"j@acme.nl"}`, `"COMPANY"`},
		{"other field", `{"name":"Jon"}`, `"COMPANY"`},
		{"type given with null email", `{"email":null,"emailType":"SHARED"}`, `"SHARED"`},
		{"type given alone", `{"emailType":"PERSONAL"}`, `"PERSONAL"`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var given map[string]json.RawMessage
			if err := json.Unmarshal([]byte(tt.given), &given); err != nil {
				t.Fatal(err)
			}
			body, err := mergeUpdate(current, given, fields)
			if err != nil {
				t.Fatal(err)
			}
			if got := string(body["emailType"]); got != tt.want {
				t.Errorf("emailType = %s, want %s", got, tt.want)
			}
		})
	}
}

func TestMergeUpdateLeavesBodyWithoutEmailTypeField(t *testing.T) {
	var current client.Contact
	if err := json.Unmarshal([]byte(`{"id":"c","name":"Jan","email":"jan@acme.nl"}`), &current); err != nil {
		t.Fatal(err)
	}
	body, err := mergeUpdate(current, map[string]json.RawMessage{"email": json.RawMessage(`null`)}, []apidocs.Field{{Name: "name"}, {Name: "email"}})
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := body["emailType"]; ok || len(body) != 2 {
		t.Errorf("body = %v", body)
	}
}

func TestClearMainFlagOfOtherKind(t *testing.T) {
	var current client.Contact
	if err := json.Unmarshal([]byte(`{"id":"c","name":"Jan","kind":"PERSON","mainPersonContact":true,"mainBusinessContact":false}`), &current); err != nil {
		t.Fatal(err)
	}
	fields := []apidocs.Field{{Name: "name"}, {Name: "kind"}, {Name: "mainPersonContact"}, {Name: "mainBusinessContact"}}
	tests := []struct {
		name, given, wantPerson string
	}{
		{"kind changed", `{"kind":"BUSINESS"}`, "false"},
		{"kind changed lower case", `{"kind":"business"}`, "false"},
		{"flag given", `{"kind":"BUSINESS","mainPersonContact":true}`, "true"},
		{"kind null keeps stored", `{"kind":null}`, "true"},
		{"same kind", `{"kind":"PERSON"}`, "true"},
		{"other field", `{"name":"Jon"}`, "true"},
		{"unknown kind", `{"kind":"ROBOT"}`, "true"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var given map[string]json.RawMessage
			if err := json.Unmarshal([]byte(tt.given), &given); err != nil {
				t.Fatal(err)
			}
			body, err := mergeUpdate(current, given, fields)
			if err != nil {
				t.Fatal(err)
			}
			if got := string(body["mainPersonContact"]); got != tt.wantPerson {
				t.Errorf("mainPersonContact = %s, want %s", got, tt.wantPerson)
			}
			if got := string(body["mainBusinessContact"]); got != "false" {
				t.Errorf("mainBusinessContact = %s, want false", got)
			}
		})
	}
}
