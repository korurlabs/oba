package docfile

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestHTMLPath(t *testing.T) {
	for _, tt := range []struct{ output, name, fallback, want string }{
		{"work/nda.html", "NDA", "id", "work/nda.html"},
		{"", "NDA Acme/B.V.", "id", "NDA-Acme-B.V.html"},
		{"", "///", "abc-123", "abc-123.html"},
	} {
		if got := HTMLPath(tt.output, tt.name, tt.fallback); got != tt.want {
			t.Errorf("HTMLPath(%q, %q) = %q, want %q", tt.output, tt.name, got, tt.want)
		}
	}
}

func TestWriteFileRefusesOverwrite(t *testing.T) {
	path := filepath.Join(t.TempDir(), "new", "nda.html")
	if err := WriteFile(path, []byte("<p>1</p>"), false); err != nil {
		t.Fatal(err)
	}
	if err := WriteFile(path, []byte("<p>2</p>"), false); err == nil || !strings.Contains(err.Error(), "--force") {
		t.Fatalf("err = %v", err)
	}
	if err := WriteFile(path, []byte("<p>2</p>"), true); err != nil {
		t.Fatal(err)
	}
	if data, _ := os.ReadFile(path); string(data) != "<p>2</p>" {
		t.Errorf("data = %q", data)
	}
}

func TestDownloadHTMLWithoutFiles(t *testing.T) {
	_, err := DownloadHTML(nil, "document d1", "oba document upload d1 <file.html>")
	if err == nil || err.Error() != "document d1 has no files yet; upload its HTML with 'oba document upload d1 <file.html>' first" {
		t.Errorf("err = %v", err)
	}
}
