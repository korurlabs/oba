package userinput

import (
	"slices"
	"testing"
)

func TestIsID(t *testing.T) {
	if !IsID(" 0b8f2a4e-6c1d-4f3a-9e2b-7d5c8a1f0e3b ") {
		t.Error("IsID refused a UUID")
	}
	for _, ref := range []string{"OPS-42", "a@b.c", "", "0b8f2a4e-6c1d-4f3a-9e2b"} {
		if IsID(ref) {
			t.Errorf("IsID(%q) = true", ref)
		}
	}
}

func TestSplitCSV(t *testing.T) {
	if got := SplitCSV(" a, ,b,,c "); !slices.Equal(got, []string{"a", "b", "c"}) {
		t.Errorf("SplitCSV = %q", got)
	}
	if got := SplitCSV(" , "); got != nil {
		t.Errorf("SplitCSV of blanks = %q", got)
	}
}
