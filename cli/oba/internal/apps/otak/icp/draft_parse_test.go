package icp

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestParseDraft(t *testing.T) {
	draft, err := ParseDraft(json.RawMessage(` {"name":"A","industry":"OTHER"} `), testFields)
	if err != nil || string(draft["name"]) != `"A"` {
		t.Fatalf("draft = %v, err = %v", draft, err)
	}
	for raw, want := range map[string]string{
		``:                       "not one JSON object: (empty)",
		`"text"`:                 `not one JSON object: "text"`,
		`{"name":`:               "not one JSON object",
		`{"name":"A","nme":"B"}`: `unknown field "nme"`,
	} {
		if _, err := ParseDraft(json.RawMessage(raw), testFields); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%q: err = %v, want %q", raw, err, want)
		}
	}
}
