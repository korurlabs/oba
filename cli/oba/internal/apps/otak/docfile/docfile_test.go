package docfile

import (
	"strings"
	"testing"
)

func TestLanguage(t *testing.T) {
	for in, want := range map[string]string{"nl": "NL", "TR": "TR", " en ": "EN", "us": "EN", "US": "EN"} {
		got, err := Language(in)
		if err != nil || got != want {
			t.Errorf("%q: got %q, %v; want %q", in, got, err, want)
		}
	}
	if _, err := Language("de"); err == nil || !strings.Contains(err.Error(), "nl, tr, en or us") {
		t.Errorf("err = %v", err)
	}
}

func TestType(t *testing.T) {
	for in, want := range map[string]string{"proposal": "PROPOSAL", "service-sheet": "SERVICE_SHEET", " Nda ": "NDA"} {
		if got, err := Type(in); err != nil || got != want {
			t.Errorf("%q: got %q, %v", in, got, err)
		}
	}
	if _, err := Type(" "); err == nil {
		t.Error("empty type accepted")
	}
	if _, err := Type("mail"); err == nil || !strings.Contains(err.Error(), "oba otak mail-template") {
		t.Errorf("mail: err = %v", err)
	}
}

func TestCheckDocumentFile(t *testing.T) {
	for _, path := range []string{"nda.html", "x/y.HTML"} {
		if err := CheckDocumentFile(path, "NDA"); err != nil {
			t.Errorf("%s: %v", path, err)
		}
	}
	bad := map[string]string{
		"mail.md": "use 'oba otak mail-template push'",
		"nda.pdf": "a NDA is an .html file, rendered to PDF on upload, not nda.pdf",
	}
	for path, want := range bad {
		if err := CheckDocumentFile(path, "NDA"); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%s: err = %v, want %q", path, err, want)
		}
	}
}

func TestCheckMailFile(t *testing.T) {
	for _, path := range []string{"a.md", "A.MD"} {
		if err := CheckMailFile(path); err != nil {
			t.Errorf("%s: %v", path, err)
		}
	}
	if err := CheckMailFile("mail.html"); err == nil || !strings.Contains(err.Error(), "a mail is a .md file") {
		t.Errorf("err = %v", err)
	}
}

func TestBaseName(t *testing.T) {
	if got := BaseName("legal/nda/nda_nl.html"); got != "nda_nl" {
		t.Errorf("got %q", got)
	}
}
