// Package guardrail cleans text that comes from outside the CLI's control, such as the output of
// Claude Code built from web pages, before the CLI prints it or sends it to the API. A web page can
// carry terminal escape sequences that recolour or rewrite the screen, set the window title, plant
// hyperlinks or write to the clipboard, and bidi controls that make text display in another order
// than it is stored. Neither belongs in a company name or a reason, so both are removed.
package guardrail

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"strings"
	"unicode/utf8"
)

const (
	esc = 0x1b
	bel = 0x07
	del = 0x7f

	// The C1 controls that open a sequence, as code points.
	c1DCS = 0x90
	c1SOS = 0x98
	c1CSI = 0x9b
	c1ST  = 0x9c
	c1OSC = 0x9d
	c1PM  = 0x9e
	c1APC = 0x9f

	// Line and paragraph breaks other than newline that text from the web carries.
	nextLine  = 0x85   // NEL
	lineSep   = 0x2028 // LINE SEPARATOR
	paragraph = 0x2029 // PARAGRAPH SEPARATOR
)

// Sanitize removes from s every terminal control: the C0 controls except newline and tab, DEL,
// the C1 controls, whole ESC and C1 sequences (CSI, OSC, DCS, SOS, PM, APC and two-character
// escapes) including their parameters, the bidi embeddings, overrides and isolates, and bytes
// that are not valid UTF-8. An unterminated sequence is removed up to the end of s.
//
// The controls and separators that break lines are not removed but become newlines: carriage
// return (CR LF becomes one newline), vertical tab, form feed, NEL and LINE SEPARATOR, and
// PARAGRAPH SEPARATOR becomes a blank line. They often stand between two words, which removing
// them would glue together, and a terminal may draw the Unicode separators as nothing at all.
// Everything else is kept as it is.
func Sanitize(s string) string {
	var b strings.Builder
	b.Grow(len(s))
	for i := 0; i < len(s); {
		r, size := utf8.DecodeRuneInString(s[i:])
		switch {
		case r == utf8.RuneError && size == 1:
			i++
		case r == esc:
			i = skipEscape(s, i+size)
		case r == c1CSI:
			i = skipCSI(s, i+size)
		case r == c1OSC || r == c1DCS || r == c1SOS || r == c1PM || r == c1APC:
			i = skipString(s, i+size)
		case r == '\r' && strings.HasPrefix(s[i+size:], "\n"):
			// CR LF: the LF that follows is the newline.
			i += size
		case isLineBreak(r):
			b.WriteByte('\n')
			i += size
		case r == paragraph:
			b.WriteString("\n\n")
			i += size
		case isDropped(r):
			i += size
		default:
			b.WriteRune(r)
			i += size
		}
	}
	return b.String()
}

// isLineBreak reports a control or separator, other than newline, that ends a line.
func isLineBreak(r rune) bool {
	return r == '\r' || r == '\v' || r == '\f' || r == nextLine || r == lineSep
}

// isDropped reports a single control character that is removed on its own.
func isDropped(r rune) bool {
	switch {
	case r == '\n' || r == '\t':
		return false
	case r < 0x20, r == del, r >= 0x80 && r <= 0x9f:
		return true
	}
	return isBidiControl(r)
}

// isBidiControl reports the embeddings and overrides (LRE, RLE, PDF, LRO, RLO) and the isolates
// (LRI, RLI, FSI, PDI), which reorder how the text after them is displayed.
func isBidiControl(r rune) bool {
	return (r >= 0x202a && r <= 0x202e) || (r >= 0x2066 && r <= 0x2069)
}

// skipEscape returns the index after the sequence whose ESC ended just before i.
func skipEscape(s string, i int) int {
	if i >= len(s) {
		return i
	}
	switch s[i] {
	case '[':
		return skipCSI(s, i+1)
	case ']', 'P', 'X', '^', '_':
		return skipString(s, i+1)
	}
	// A two-character escape such as ESC c (reset) or ESC 7 (save cursor): drop the next rune.
	_, size := utf8.DecodeRuneInString(s[i:])
	return i + size
}

// skipCSI returns the index after a control sequence whose introducer ended just before i: it
// runs through the parameter and intermediate bytes to the final byte in 0x40..0x7e.
func skipCSI(s string, i int) int {
	for i < len(s) {
		c := s[i]
		i++
		if c >= 0x40 && c <= 0x7e {
			return i
		}
		if c < 0x20 || c >= 0x80 {
			// Not a parameter or intermediate byte: the sequence is malformed and ends here.
			// The byte itself is left for Sanitize to judge.
			return i - 1
		}
	}
	return i
}

// skipString returns the index after a control string (OSC, DCS, SOS, PM, APC) whose
// introducer ended just before i. It ends at BEL, ESC \ or the C1 string terminator.
func skipString(s string, i int) int {
	for i < len(s) {
		r, size := utf8.DecodeRuneInString(s[i:])
		switch r {
		case bel, c1ST:
			return i + size
		case esc:
			if i+1 < len(s) && s[i+1] == '\\' {
				return i + 2
			}
			// Another ESC also ends the string; the new sequence starts here.
			return i
		}
		i += size
	}
	return i
}

// CleanJSON applies Sanitize to every string value and every object key in raw, at any depth,
// and returns the result as compact JSON. Numbers keep their text. raw must hold exactly one
// JSON value. Two keys of one object that become equal after cleaning are an error, since
// keeping either would silently drop the other.
func CleanJSON(raw json.RawMessage) (json.RawMessage, error) {
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	var value any
	if err := dec.Decode(&value); err != nil {
		return nil, fmt.Errorf("the output is not JSON: %w", err)
	}
	if _, err := dec.Token(); !errors.Is(err, io.EOF) {
		return nil, errors.New("the output holds more than one JSON value")
	}
	cleaned, err := cleanValue(value)
	if err != nil {
		return nil, err
	}
	var out bytes.Buffer
	enc := json.NewEncoder(&out)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(cleaned); err != nil {
		return nil, fmt.Errorf("failed to encode the cleaned output: %w", err)
	}
	return bytes.TrimRight(out.Bytes(), "\n"), nil
}

func cleanValue(value any) (any, error) {
	switch v := value.(type) {
	case string:
		return Sanitize(v), nil
	case []any:
		for i, item := range v {
			cleaned, err := cleanValue(item)
			if err != nil {
				return nil, err
			}
			v[i] = cleaned
		}
		return v, nil
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, item := range v {
			cleanKey := Sanitize(key)
			if _, taken := out[cleanKey]; taken {
				return nil, fmt.Errorf("the output has two keys that read %q once control characters are removed", cleanKey)
			}
			cleaned, err := cleanValue(item)
			if err != nil {
				return nil, err
			}
			out[cleanKey] = cleaned
		}
		return out, nil
	}
	// Numbers, booleans and null carry no text.
	return value, nil
}

// OneLine is s sanitized (see Sanitize) and collapsed onto one line, its whitespace runs joined by
// single spaces, for a table cell, a list row or a header built from text of the web or a user.
func OneLine(s string) string {
	return strings.Join(strings.Fields(Sanitize(s)), " ")
}
