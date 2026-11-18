package docfile

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSafeName(t *testing.T) {
	for in, want := range map[string]string{
		"NDA Acme B.V.":       "NDA-Acme-B.V",
		"../../etc/passwd":    "etc-passwd",
		".hidden":             "hidden",
		"Gizlilik Sözleşmesi": "Gizlilik-Sözleşmesi",
		"a\x1b[31mb":          "a-31mb",
		"///":                 "",
	} {
		if got := SafeName(in); got != want {
			t.Errorf("%q: got %q, want %q", in, got, want)
		}
	}
}

func TestWriteFilesRefusesOverwrite(t *testing.T) {
	dir := t.TempDir()
	existing := filepath.Join(dir, "NDA.pdf")
	if err := os.WriteFile(existing, []byte("old"), 0o600); err != nil {
		t.Fatal(err)
	}
	files := []File{{Ext: ExtHTML, Data: []byte("<p>")}, {Ext: ExtPDF, Data: []byte("%PDF-")}}

	if _, err := WriteFiles(dir, "NDA", "id", files, false); err == nil || !strings.Contains(err.Error(), "--force") {
		t.Fatalf("err = %v", err)
	}
	if _, err := os.Stat(filepath.Join(dir, "NDA.html")); err == nil {
		t.Error("the HTML was written although the PDF exists")
	}

	paths, err := WriteFiles(dir, "NDA", "id", files, true)
	if err != nil || len(paths) != 2 {
		t.Fatalf("paths %v, err %v", paths, err)
	}
	if data, _ := os.ReadFile(existing); string(data) != "%PDF-" {
		t.Errorf("pdf = %q", data)
	}
}

func TestWriteFilesFallsBackToID(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "new")
	paths, err := WriteFiles(dir, "///", "abc-123", []File{{Ext: ExtMail, Data: []byte("S\n\nB\n")}}, false)
	if err != nil || len(paths) != 1 || paths[0] != filepath.Join(dir, "abc-123.md") {
		t.Fatalf("paths %v, err %v", paths, err)
	}
}
