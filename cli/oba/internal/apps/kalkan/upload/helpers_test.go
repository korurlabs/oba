package upload

import (
	"os"
	"path/filepath"
	"testing"
)

// tree writes files (relative path to content) under a new directory and returns it.
func tree(t *testing.T, files map[string]string) string {
	t.Helper()
	dir := t.TempDir()
	for name, content := range files {
		full := filepath.Join(dir, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(full), 0o750); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(full, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return dir
}

func mustRead(t *testing.T, dir, target string) *Bundle {
	t.Helper()
	b, err := ReadBundle(dir, target, "EN")
	if err != nil {
		t.Fatalf("ReadBundle() = %v", err)
	}
	return b
}

func pagePaths(b *Bundle) []string {
	var out []string
	for _, p := range b.Pages {
		out = append(out, p.Path)
	}
	return out
}

func findPage(t *testing.T, b *Bundle, path string) Page {
	t.Helper()
	for _, p := range b.Pages {
		if p.Path == path {
			return p
		}
	}
	t.Fatalf("no page %s in %v", path, pagePaths(b))
	return Page{}
}

func titleOf(p Page, locale string) string {
	for _, tr := range p.Translations {
		if tr.Locale == locale {
			return tr.Title
		}
	}
	return "<none>"
}
