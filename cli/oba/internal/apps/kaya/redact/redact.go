// Package redact decides what in captured text looks like a secret and replaces it with a marker
// before the text leaves the machine. It is the one definition of a secret-like value in the CLI:
// session capture redacts prompts and commands with Text, and a check that refuses to write a
// secret names the lines with Find.
package redact

import (
	"regexp"
	"sort"
	"strings"
	"unicode/utf8"
)

// Marker replaces every redacted value.
const Marker = "[redacted]"

// space is the Unicode whitespace of Python's `\s`, written out because Go's `\s` is ASCII only. A
// value is cut at the first of these, so a narrower set would let the rest of a value through.
const space = `\t\n\v\f\r\x{1c}-\x{20}\x{85}\x{a0}\x{1680}\x{2000}-\x{200a}\x{2028}\x{2029}\x{202f}\x{205f}\x{3000}`

// value is a quoted string or, failing that, everything up to the next whitespace. An unterminated
// quote is therefore taken as the start of an unquoted value.
const value = `("[^"]*"|'[^']*'|[^` + space + `]+)`

// The three rules that need no lookbehind. Each captures the value in group 2; Go's regexp engine
// keeps the leftmost-first preference of Python's backtracking, so the same alternative wins.
var patterns = []*regexp.Regexp{
	// A --token or --password flag with its value after `=` or whitespace.
	regexp.MustCompile(`(--(?:token|password)(?:=|[` + space + `]+))` + value),
	// A -p flag that stands on its own, as in `mysql -p secret`; `-p5432` is a port.
	regexp.MustCompile(`((?:^|[` + space + `])-p[` + space + `]+)` + value),
	// An Authorization header up to the end of the line or a quote. Python folds İ and ı onto i
	// under IGNORECASE and Go does not, so both are spelled out.
	regexp.MustCompile(`(?i)(author[i\x{130}\x{131}]zat[i\x{130}\x{131}]on:[` + space + `]*)([^'"\n]+)`),
}

// span is the byte range of one value in the text a pass read.
type span struct {
	start, end int
}

// pass finds the values one rule redacts, in order and without overlap.
type pass func(s string) []span

var passes = []pass{
	regexpPass(patterns[0]),
	regexpPass(patterns[1]),
	regexpPass(patterns[2]),
	namedValues,
}

// Text returns s with every secret-like value replaced by Marker. The rules run one after the other,
// each on the output of the one before, so a later rule sees the markers of an earlier one.
func Text(s string) string {
	for _, p := range passes {
		s = replace(s, p(s))
	}
	return s
}

// Find returns the 1-based numbers of the lines of s that hold a secret-like value, in ascending
// order, or nil. A value that spans lines, such as a quoted one, names each of its lines. A value
// that already is Marker is not a secret, so text that Text has redacted yields nil. The values
// themselves are never returned.
func Find(s string) []int {
	lines := make([]int32, len(s))
	line := int32(1)
	for i := 0; i < len(s); i++ {
		lines[i] = line
		if s[i] == '\n' {
			line++
		}
	}
	found := map[int32]bool{}
	for _, p := range passes {
		spans := p(s)
		for _, sp := range spans {
			if s[sp.start:sp.end] == Marker {
				continue
			}
			for l := lines[sp.start]; l <= lines[sp.end-1]; l++ {
				found[l] = true
			}
		}
		s, lines = replaceTagged(s, lines, spans)
	}
	if len(found) == 0 {
		return nil
	}
	numbers := make([]int, 0, len(found))
	for l := range found {
		numbers = append(numbers, int(l))
	}
	sort.Ints(numbers)
	return numbers
}

func regexpPass(re *regexp.Regexp) pass {
	return func(s string) []span {
		matches := re.FindAllStringSubmatchIndex(s, -1)
		spans := make([]span, 0, len(matches))
		for _, m := range matches {
			spans = append(spans, span{m[4], m[5]})
		}
		return spans
	}
}

// replace returns s with each span replaced by Marker.
func replace(s string, spans []span) string {
	if len(spans) == 0 {
		return s
	}
	var b strings.Builder
	b.Grow(len(s))
	last := 0
	for _, sp := range spans {
		b.WriteString(s[last:sp.start])
		b.WriteString(Marker)
		last = sp.end
	}
	b.WriteString(s[last:])
	return b.String()
}

// replaceTagged is replace that also carries the line number of every byte, giving the bytes of
// a marker the line where the value it replaces started.
func replaceTagged(s string, lines []int32, spans []span) (string, []int32) {
	if len(spans) == 0 {
		return s, lines
	}
	tagged := make([]int32, 0, len(lines))
	last := 0
	for _, sp := range spans {
		tagged = append(tagged, lines[last:sp.start]...)
		for range len(Marker) {
			tagged = append(tagged, lines[sp.start])
		}
		last = sp.end
	}
	tagged = append(tagged, lines[last:]...)
	return replace(s, spans), tagged
}

// namedValues finds the value of a name that ends in TOKEN, SECRET, PASSWORD or KEY followed by `=`,
// as in `GITHUB_TOKEN=ghp_...`. A name is a maximal run of name characters: since `=` is not one,
// the name can only end where the run ends, so each run is checked once and the scan stays linear
// on a long word, where a regular expression without lookbehind would retry inside the run.
func namedValues(s string) []span {
	var spans []span
	var noDouble, noSingle bool
	i := 0
	for i < len(s) {
		r, w := utf8.DecodeRuneInString(s[i:])
		if !isNameRune(r) {
			i += w
			continue
		}
		start := i
		j := i + w
		for j < len(s) {
			r, w = utf8.DecodeRuneInString(s[j:])
			if !isNameRune(r) {
				break
			}
			j += w
		}
		if j < len(s) && s[j] == '=' && endsInSecretWord(s[start:j]) {
			if end := valueEnd(s, j+1, &noDouble, &noSingle); end > j+1 {
				spans = append(spans, span{j + 1, end})
				i = end
				continue
			}
		}
		i = j
	}
	return spans
}

// valueEnd returns the end of the value that starts at k, or k when there is none. noDouble and
// noSingle remember that no closing quote of that kind is left, so that many unterminated quotes
// do not each search to the end of the text.
func valueEnd(s string, k int, noDouble, noSingle *bool) int {
	if k >= len(s) {
		return k
	}
	if q := s[k]; q == '"' || q == '\'' {
		none := noDouble
		if q == '\'' {
			none = noSingle
		}
		if !*none {
			if closing := strings.IndexByte(s[k+1:], q); closing >= 0 {
				return k + 1 + closing + 1
			}
			*none = true
		}
	}
	end := k
	for end < len(s) {
		r, w := utf8.DecodeRuneInString(s[end:])
		if isSpace(r) {
			break
		}
		end += w
	}
	return end
}

// endsInSecretWord reports whether name ends, case-insensitively, in TOKEN, SECRET, PASSWORD or KEY.
func endsInSecretWord(name string) bool {
	var tail [8]rune
	n := 0
	for n < len(tail) && len(name) > 0 {
		r, w := utf8.DecodeLastRuneInString(name)
		tail[len(tail)-1-n] = foldName(r)
		name = name[:len(name)-w]
		n++
	}
	folded := string(tail[len(tail)-n:])
	for _, word := range []string{"token", "secret", "password", "key"} {
		if strings.HasSuffix(folded, word) {
			return true
		}
	}
	return false
}

// isNameRune reports whether r is a name character: `[A-Za-z0-9_.-]` as Python matches it under
// IGNORECASE, which also takes the non-ASCII letters that fold onto an ASCII one.
func isNameRune(r rune) bool {
	switch {
	case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9':
		return true
	case r == '_', r == '.', r == '-':
		return true
	case r == '\u0130', r == '\u0131', r == '\u017f', r == '\u212a':
		return true
	}
	return false
}

// foldName maps a name character to the lower-case ASCII letter it matches under Python's
// IGNORECASE, and any other name character to itself.
func foldName(r rune) rune {
	switch {
	case r >= 'A' && r <= 'Z':
		return r - 'A' + 'a'
	case r == '\u0130', r == '\u0131':
		return 'i'
	case r == '\u017f':
		return 's'
	case r == '\u212a':
		return 'k'
	}
	return r
}

// isSpace reports whether r is in Python's Unicode `\s`, the set of space.
func isSpace(r rune) bool {
	switch {
	case r >= '\t' && r <= '\r', r >= 0x1c && r <= 0x20:
		return true
	case r == 0x85, r == 0xa0, r == 0x1680, r >= 0x2000 && r <= 0x200a:
		return true
	case r == 0x2028, r == 0x2029, r == 0x202f, r == 0x205f, r == 0x3000:
		return true
	}
	return false
}
