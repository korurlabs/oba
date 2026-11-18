package icp

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestPrintDraft(t *testing.T) {
	var out strings.Builder
	PrintDraft(&out, map[string]json.RawMessage{
		"name": json.RawMessage(`"A"`), "country": json.RawMessage("null"),
		"description": json.RawMessage(`"line one\nline two"`),
	}, testFields)
	want := "Draft ICP:\n" +
		"  name:         A\n" +
		"  industry:     -\n" +
		"  country:      -\n" +
		"  description:  line one\n" +
		"                line two\n"
	if out.String() != want {
		t.Errorf("out =\n%q\nwant\n%q", out.String(), want)
	}
}
