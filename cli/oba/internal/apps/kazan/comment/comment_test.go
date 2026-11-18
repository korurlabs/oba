package comment

import "testing"

func TestPreviewCutsLongContentToOneLine(t *testing.T) {
	long := ""
	for range 70 {
		long += "ä"
	}
	if got := preview(long); len([]rune(got)) != previewRunes || got[len(got)-3:] != "..." {
		t.Errorf("preview = %q", got)
	}
	if got := preview("two\nlines"); got != "two lines" {
		t.Errorf("preview = %q", got)
	}
}

func TestAuthorFallsBackToEmail(t *testing.T) {
	if got := author("Ada", "Lovelace", "a@b.c"); got != "Ada Lovelace" {
		t.Errorf("got %q", got)
	}
	if got := author("", "", "a@b.c"); got != "a@b.c" {
		t.Errorf("got %q", got)
	}
	if got := author("", "", ""); got != "-" {
		t.Errorf("got %q", got)
	}
}
