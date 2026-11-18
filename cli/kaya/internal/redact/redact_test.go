package redact

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

// namedCase is one string of testdata/redact.json with the Python recorder's output.
type namedCase struct {
	Name     string `json:"name"`
	Input    string `json:"input"`
	Expected string `json:"expected"`
}

// generatedCorpus is testdata/generated.json: random joins of secret-like fragments.
type generatedCorpus struct {
	Seed  int `json:"seed"`
	Cases []struct {
		Input    string `json:"input"`
		Expected string `json:"expected"`
	} `json:"cases"`
}

func readJSON(t *testing.T, name string, into any) {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("testdata", name))
	if err != nil {
		t.Fatalf("read %s: %v", name, err)
	}
	if err := json.Unmarshal(data, into); err != nil {
		t.Fatalf("decode %s: %v", name, err)
	}
}

func TestTextEqualsPythonOnNamedCorpus(t *testing.T) {
	var cases []namedCase
	readJSON(t, "redact.json", &cases)
	if len(cases) == 0 {
		t.Fatal("redact.json holds no case")
	}
	for _, c := range cases {
		t.Run(c.Name, func(t *testing.T) {
			if got := Text(c.Input); got != c.Expected {
				t.Errorf("Text(%q)\n got  %q\n want %q", c.Input, got, c.Expected)
			}
		})
	}
}

func TestTextRedactsAtLeastPythonOnGeneratedCorpus(t *testing.T) {
	var corpus generatedCorpus
	readJSON(t, "generated.json", &corpus)
	if len(corpus.Cases) == 0 {
		t.Fatal("generated.json holds no case")
	}
	for i, c := range corpus.Cases {
		got := Text(c.Input)
		if got == c.Expected {
			continue
		}
		if !keepsNoMoreThan(c.Input, c.Expected, removedByText(c.Input)) {
			t.Errorf("case %d: Text(%q) keeps a value Python redacted\n got  %q\n want at least %q",
				i, c.Input, got, c.Expected)
		}
	}
}

// keepsNoMoreThan reports whether removed, the bytes of input that Text replaced, covers every value
// that want replaced. want is input with values replaced by markers, so its pieces between markers
// are laid onto input in order; each gap between two pieces is a value Python removed and must be
// removed here as well, unless the gap is a marker that input already held. Any layout that fits
// is accepted, because the corpus does not record where Python's values were.
func keepsNoMoreThan(input, want string, removed []bool) bool {
	removedBefore := make([]int, len(input)+1)
	for i, r := range removed {
		removedBefore[i+1] = removedBefore[i]
		if r {
			removedBefore[i+1]++
		}
	}
	pieces := strings.Split(want, Marker)
	if !strings.HasPrefix(input, pieces[0]) {
		return false
	}
	ends := map[int]bool{len(pieces[0]): true}
	for _, piece := range pieces[1:] {
		next := map[int]bool{}
		for end := range ends {
			for start := end; start+len(piece) <= len(input); start++ {
				if input[start:start+len(piece)] != piece {
					continue
				}
				gap := input[end:start]
				allRemoved := gap != "" && removedBefore[start]-removedBefore[end] == len(gap)
				if allRemoved || gap == Marker {
					next[start+len(piece)] = true
				}
			}
		}
		ends = next
	}
	return ends[len(input)]
}

// removedByText marks the bytes of input that Text replaces by markers, across all passes.
func removedByText(input string) []bool {
	removed := make([]bool, len(input))
	s := input
	origin := make([]int, len(s))
	for i := range origin {
		origin[i] = i
	}
	for _, p := range passes {
		spans := p(s)
		next := make([]int, 0, len(origin))
		last := 0
		for _, sp := range spans {
			next = append(next, origin[last:sp.start]...)
			for _, o := range origin[sp.start:sp.end] {
				if o >= 0 {
					removed[o] = true
				}
			}
			for range len(Marker) {
				next = append(next, -1)
			}
			last = sp.end
		}
		next = append(next, origin[last:]...)
		s, origin = replace(s, spans), next
	}
	return removed
}

func TestFindNamesLines(t *testing.T) {
	cases := []struct {
		name  string
		input string
		want  []int
	}{
		{"none", "nothing here\nat all", nil},
		{"named value", "first\nAPI_KEY=abc\nthird", []int{2}},
		{"token flag across a newline", "login --token\nabc", []int{2}},
		{"quoted value across lines", "x\nDB_PASSWORD=\"a\nb\" tail\nlast", []int{2, 3}},
		{"several rules", "-p one\nok\nAuthorization: Bearer x\nS_TOKEN=y", []int{1, 3, 4}},
		{"already redacted", "--token [redacted]\nAPI_KEY=[redacted]", nil},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := Find(c.input); !reflect.DeepEqual(got, c.want) {
				t.Errorf("Find(%q) = %v, want %v", c.input, got, c.want)
			}
		})
	}
}

func TestFindReturnsNoValues(t *testing.T) {
	if got := Find(Text("A_KEY=1\n--password p\n")); got != nil {
		t.Errorf("Find of redacted text = %v, want nil", got)
	}
}
