package guardrail

import (
	"encoding/json"
	"testing"
)

func TestSanitize(t *testing.T) {
	cases := map[string]struct{ in, want string }{
		"plain text":             {"Acme B.V. – Ünïcode 日本", "Acme B.V. – Ünïcode 日本"},
		"newline and tab kept":   {"a\n\tb", "a\n\tb"},
		"CSI colour":             {"\x1b[31mred\x1b[0m", "red"},
		"CSI cursor and clear":   {"a\x1b[2J\x1b[1;1Hb", "ab"},
		"CSI private mode":       {"a\x1b[?25lb", "ab"},
		"CSI unterminated":       {"a\x1b[12;", "a"},
		"CSI malformed":          {"a\x1b[1\nb", "a\nb"},
		"OSC title BEL":          {"a\x1b]0;pwned\x07b", "ab"},
		"OSC hyperlink ST":       {"\x1b]8;;https://evil.example\x1b\\click\x1b]8;;\x1b\\", "click"},
		"OSC clipboard":          {"a\x1b]52;c;ZXZpbA==\x07b", "ab"},
		"OSC unterminated":       {"a\x1b]0;title", "a"},
		"OSC ended by ESC":       {"a\x1b]0;t\x1b[1mb", "ab"},
		"DCS":                    {"a\x1bPq#0\x1b\\b", "ab"},
		"APC":                    {"a\x1b_payload\x1b\\b", "ab"},
		"two-character escape":   {"a\x1bcb\x1b7c", "abc"},
		"lone ESC at end":        {"a\x1b", "a"},
		"C1 CSI":                 {"a\u009b31mb", "ab"},
		"C1 OSC with C1 ST":      {"a\u009d0;t\u009cb", "ab"},
		"C1 single controls":     {"a\u0084\u0080b", "ab"},
		"C0 controls":            {"a\x00\x07\x08\x0eb", "ab"},
		"CR LF":                  {"their\r\nrevenue", "their\nrevenue"},
		"lone CR":                {"their\rrevenue", "their\nrevenue"},
		"CR CR LF":               {"a\r\r\nb", "a\n\nb"},
		"vertical tab":           {"their\x0brevenue", "their\nrevenue"},
		"form feed":              {"their\x0crevenue", "their\nrevenue"},
		"NEL":                    {"their\u0085revenue", "their\nrevenue"},
		"line separator":         {"their\u2028revenue", "their\nrevenue"},
		"paragraph separator":    {"First.\u2029Second.", "First.\n\nSecond."},
		"no-break space kept":    {"their\u00a0revenue", "their\u00a0revenue"},
		"DEL":                    {"a\x7fb", "ab"},
		"bidi override":          {"a\u202eexe.txt\u202c", "aexe.txt"},
		"bidi embedding":         {"\u202a\u202b\u202dx", "x"},
		"bidi isolates":          {"\u2066\u2067\u2068x\u2069", "x"},
		"invalid UTF-8":          {"a\xffb\x9bc", "abc"},
		"marks and joiners kept": {"a\u200db\u200fc", "a\u200db\u200fc"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			if got := Sanitize(tc.in); got != tc.want {
				t.Errorf("Sanitize(%q) = %q, want %q", tc.in, got, tc.want)
			}
		})
	}
}

func TestCleanJSONNested(t *testing.T) {
	raw := json.RawMessage(`{
		"candidates": [
			{"name": "Acme\u001b[31m B.V.", "website": "https://acme.example", "score": 1.50, "ok": true, "x": null},
			{"name": "\u202eBeta", "tags": ["a\u001b]0;t\u0007b", ["\u009b2Jdeep"]], "meta": {"k\u001b[0mey": "<v&>"}}
		]
	}`)
	got, err := CleanJSON(raw)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"candidates":[{"name":"Acme B.V.","ok":true,"score":1.50,"website":"https://acme.example","x":null},` +
		`{"meta":{"key":"<v&>"},"name":"Beta","tags":["ab",["deep"]]}]}`
	if string(got) != want {
		t.Errorf("CleanJSON =\n%s\nwant\n%s", got, want)
	}
}

func TestCleanJSONScalars(t *testing.T) {
	cases := map[string]string{
		`"a\u001b[1mb"`:        `"ab"`,
		`12345678901234567890`: `12345678901234567890`,
		`null`:                 `null`,
		`[]`:                   `[]`,
	}
	for in, want := range cases {
		got, err := CleanJSON(json.RawMessage(in))
		if err != nil {
			t.Fatalf("%s: %v", in, err)
		}
		if string(got) != want {
			t.Errorf("CleanJSON(%s) = %s, want %s", in, got, want)
		}
	}
}

func TestCleanJSONRefuses(t *testing.T) {
	cases := map[string]string{
		"not JSON":            `{"a":`,
		"two values":          `{} {}`,
		"keys collide":        `{"name":1,"na\u001b[0mme":2}`,
		"nested keys collide": `[{"x":{"k":1,"k\u202e":2}}]`,
	}
	for name, in := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := CleanJSON(json.RawMessage(in)); err == nil {
				t.Error("want an error")
			}
		})
	}
}

// Separators between words survive as newlines in JSON strings too, so the draft that is shown
// and sent never reads "theirrevenue".
func TestCleanJSONKeepsWordBreaks(t *testing.T) {
	got, err := CleanJSON(json.RawMessage(`{"description":"their revenue and their\rcosts"}`))
	if err != nil {
		t.Fatal(err)
	}
	if want := `{"description":"their\nrevenue and their\ncosts"}`; string(got) != want {
		t.Errorf("CleanJSON = %s, want %s", got, want)
	}
}
