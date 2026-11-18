package jsonedit

import (
	"encoding/json"
	"testing"
)

func TestParse_refusesDuplicateKeysAndNonObjects(t *testing.T) {
	for _, input := range []string{`{"a":1,"a":2}`, `[1]`, `{"a":`, `"text"`} {
		if _, err := Parse([]byte(input)); err == nil {
			t.Errorf("Parse(%s) accepted", input)
		}
	}
	empty, err := Parse([]byte("  \n"))
	if err != nil {
		t.Fatal(err)
	}
	out, err := empty.Marshal()
	if err != nil || string(out) != "{}\n" {
		t.Fatalf("empty = %q, %v", out, err)
	}
}

func TestSet_keepsOrderAndValuesAsWritten(t *testing.T) {
	object, err := Parse([]byte(`{"z":1,"a":{"keep":"<b>"},"m":[1,2]}`))
	if err != nil {
		t.Fatal(err)
	}
	if err := object.Set("a", json.RawMessage(`{"keep":"<b>","x":true}`)); err != nil {
		t.Fatal(err)
	}
	if err := object.Set("new", "a&b"); err != nil {
		t.Fatal(err)
	}
	out, err := object.Marshal()
	if err != nil {
		t.Fatal(err)
	}
	want := "{\n  \"z\": 1,\n  \"a\": {\n    \"keep\": \"<b>\",\n    \"x\": true\n  },\n  \"m\": [\n    1,\n    2\n  ],\n  \"new\": \"a&b\"\n}\n"
	if string(out) != want {
		t.Fatalf("got\n%s\nwant\n%s", out, want)
	}
}
