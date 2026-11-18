package terminal

import (
	"bufio"
	"io"
	"strings"
	"testing"
)

func TestParseObject(t *testing.T) {
	tests := []struct {
		name, in, wantErr string
	}{
		{"object", ` {"a": 25000.10} `, ""},
		{"empty object", `{}`, ""},
		{"empty", "", "no JSON on stdin"},
		{"blank", " \n\t", "no JSON on stdin"},
		{"invalid", `{"a":`, "stdin is not valid JSON"},
		{"trailing data", `{"a":1} x`, "stdin is not valid JSON"},
		{"array", `[]`, "not an array"},
		{"number", `12`, "not a number"},
		{"null", `null`, "not null"},
		{"boolean", `true`, "not a boolean"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			obj, err := ParseObject([]byte(tt.in))
			if tt.wantErr == "" {
				if err != nil {
					t.Fatal(err)
				}
				if v, ok := obj["a"]; ok && string(v) != "25000.10" {
					t.Errorf("a = %s", v)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("err = %v, want %q", err, tt.wantErr)
			}
		})
	}
}

func TestReadStdinCap(t *testing.T) {
	if _, err := ReadStdin(strings.NewReader(strings.Repeat("a", MaxStdinBytes+1))); err == nil {
		t.Error("expected an error above the cap")
	}
	if _, err := ReadStdin(strings.NewReader(strings.Repeat("a", MaxStdinBytes))); err != nil {
		t.Errorf("at the cap: %v", err)
	}
}

func TestReadText(t *testing.T) {
	tests := map[string]string{
		"":                                  "",
		" \n\n":                             "",
		"one line\n":                        "one line",
		"\nFirst.\n\n\nSecond,\nthird.\n\n": "First.\n\n\nSecond,\nthird.",
		"Windows\r\n\r\nlines\r\n":          "Windows\n\nlines",
		"no newline at the end":             "no newline at the end",
	}
	for in, want := range tests {
		got, err := ReadText(bufio.NewReader(strings.NewReader(in)))
		if err != nil || got != want {
			t.Errorf("%q: got %q, %v want %q", in, got, err, want)
		}
	}
}

func TestReadTextCap(t *testing.T) {
	if _, err := ReadText(bufio.NewReader(strings.NewReader(strings.Repeat("a", MaxTextBytes+1)))); err == nil {
		t.Error("expected an error above the cap")
	}
	if _, err := ReadText(bufio.NewReader(strings.NewReader(strings.Repeat("a", MaxTextBytes)))); err != nil {
		t.Errorf("at the cap: %v", err)
	}
}

// ReadText stops at Ctrl-D, and the same reader goes on reading the lines typed after it, as a
// terminal delivers them.
func TestReadTextThenConfirm(t *testing.T) {
	src := &lineReads{reads: []string{"First.\n", "\n", "Second.\n", "", "y\n"}}
	in := bufio.NewReader(src)
	text, err := ReadText(in)
	if err != nil || text != "First.\n\nSecond." {
		t.Fatalf("text = %q, err = %v", text, err)
	}
	var out strings.Builder
	yes, err := Confirm(in, src, &out, "Q?")
	if err != nil || !yes {
		t.Fatalf("yes = %v, err = %v", yes, err)
	}
}

func TestConfirm(t *testing.T) {
	again := "Q? (y/n) Please answer y or n.\n"
	tests := []struct {
		name    string
		reads   []string
		want    bool
		wantOut string
	}{
		{"y", []string{"y\n"}, true, "Q? (y/n) "},
		{"yes any case", []string{" YeS \n"}, true, "Q? (y/n) "},
		{"n", []string{"N\n"}, false, "Q? (y/n) "},
		{"no", []string{"no\n"}, false, "Q? (y/n) "},
		{"junk then yes", []string{"yep\n", "\n", "y es\n", "yes\n"}, true, again + again + again + "Q? (y/n) "},
		{"junk then no", []string{"maybe\n", "n\n"}, false, again + "Q? (y/n) "},
		{"end of input", nil, false, "Q? (y/n) \n"},
		{"junk then end of input", []string{"maybe\n"}, false, again + "Q? (y/n) \n"},
		{"y without newline", []string{"y"}, true, "Q? (y/n) "},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			src := &lineReads{reads: tt.reads}
			var out strings.Builder
			got, err := Confirm(bufio.NewReader(src), src, &out, "Q?")
			if err != nil || got != tt.want {
				t.Errorf("got %v, %v want %v", got, err, tt.want)
			}
			if out.String() != tt.wantOut {
				t.Errorf("out = %q, want %q", out.String(), tt.wantOut)
			}
		})
	}
}

// Lines already read into the buffer before the question, such as the rest of a paste, are
// discarded: they can neither answer it nor make it ask again.
func TestConfirmDiscardsBufferedInput(t *testing.T) {
	src := &lineReads{reads: []string{"y\nyes\npasted line\n", "n\n"}}
	in := bufio.NewReader(src)
	if _, err := in.Peek(1); err != nil {
		t.Fatal(err)
	}
	var out strings.Builder
	got, err := Confirm(in, src, &out, "Q?")
	if err != nil || got {
		t.Fatalf("got %v, %v want a no from the line typed after the question", got, err)
	}
	if out.String() != "Q? (y/n) " {
		t.Errorf("out = %q", out.String())
	}
}

// Ask discards what waits in the buffer before it asks, and reads the line typed after it.
func TestAskDiscardsBufferedInput(t *testing.T) {
	src := &lineReads{reads: []string{"1\nall\ny\n", " 2 \n"}}
	in := bufio.NewReader(src)
	var out strings.Builder
	first, eof, err := Ask(in, src, &out, "A: ")
	if err != nil || eof || first != "1" {
		t.Fatalf("first = %q, %v, %v", first, eof, err)
	}
	second, eof, err := Ask(in, src, &out, "B: ")
	if err != nil || eof || second != "2" {
		t.Fatalf("second = %q, %v, %v", second, eof, err)
	}
	if out.String() != "A: B: " {
		t.Errorf("out = %q", out.String())
	}
	if _, eof, err := Ask(in, src, &out, "C: "); err != nil || !eof {
		t.Errorf("end of input: eof = %v, err = %v", eof, err)
	}
}

// lineReads answers each Read with the next string, as a terminal in line mode answers with one
// line; an empty string is the end of input that Ctrl-D gives, after which reading goes on.
type lineReads struct{ reads []string }

func (r *lineReads) Read(p []byte) (int, error) {
	if len(r.reads) == 0 {
		return 0, io.EOF
	}
	next := r.reads[0]
	r.reads = r.reads[1:]
	if next == "" {
		return 0, io.EOF
	}
	if len(next) > len(p) {
		r.reads = append([]string{next[len(p):]}, r.reads...)
	}
	return copy(p, next), nil
}
