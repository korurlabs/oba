package icp

import (
	"strings"
	"testing"
)

func TestBrief(t *testing.T) {
	brief := Brief("  textile makers in NL  ", testFields)
	for _, part := range []string{
		"Description, as the user typed it:\ntextile makers in NL\n",
		`"industry": "SOFTWARE" | "OTHER",  (required)`,
		"no personal data",
	} {
		if !strings.Contains(brief, part) {
			t.Errorf("brief lacks %q:\n%s", part, brief)
		}
	}
}

func TestDraftSchema(t *testing.T) {
	schema, err := DraftSchema(testFields)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(schema, `"required":["name","industry"]`) || !strings.Contains(schema, `"additionalProperties":false`) {
		t.Errorf("schema = %s", schema)
	}
}
