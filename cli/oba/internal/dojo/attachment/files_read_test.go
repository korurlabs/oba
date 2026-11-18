package attachment

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeTestFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestReadFilesNamesAndTypes(t *testing.T) {
	dir := t.TempDir()
	files, err := ReadFiles([]string{writeTestFile(t, dir, "shot.png", "png"), " ", writeTestFile(t, dir, "notes", "plain")})
	if err != nil {
		t.Fatal(err)
	}
	if len(files) != 2 || files[0].FileName != "shot.png" || files[0].ContentType != "image/png" ||
		files[1].ContentType != "text/plain; charset=utf-8" {
		t.Errorf("files = %+v", files)
	}
}

func TestReadFilesWithoutAPathIsEmpty(t *testing.T) {
	files, err := ReadFiles([]string{" ", ""})
	if err != nil || len(files) != 0 {
		t.Errorf("files = %+v, err = %v", files, err)
	}
}

func TestReadFilesExpandsTheHomeDirectory(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)
	writeTestFile(t, home, "a.txt", "a")
	files, err := ReadFiles([]string{"~/a.txt"})
	if err != nil || len(files) != 1 || files[0].FileName != "a.txt" {
		t.Fatalf("files = %+v, err = %v", files, err)
	}
}

func TestReadFilesRefuses(t *testing.T) {
	dir := t.TempDir()
	a := writeTestFile(t, dir, "a.txt", "same")
	b := writeTestFile(t, dir, "b.txt", "same")
	empty := writeTestFile(t, dir, "empty.txt", "")
	tests := map[string][]string{
		"has the same content as":   {a, b},
		"is empty":                  {empty},
		"is not a regular file":     {dir},
		"no such file or directory": {filepath.Join(dir, "missing")},
	}
	for want, paths := range tests {
		if _, err := ReadFiles(paths); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%v: err = %v, want %q", paths, err, want)
		}
	}
}

func TestReadFilesDetectsTheMediaType(t *testing.T) {
	dir := t.TempDir()
	paths := []string{
		writeTestFile(t, dir, "voorstel.pdf", "%PDF-1.7"),
		writeTestFile(t, dir, "logo.png", "not really a png"),
		writeTestFile(t, dir, "README", "plain text"),
		writeTestFile(t, dir, "blob", "\x00\x01\x02\x03"),
	}
	files, err := ReadFiles(paths)
	if err != nil {
		t.Fatal(err)
	}
	want := []struct{ name, contentType, data string }{
		{"voorstel.pdf", "application/pdf", "%PDF-1.7"},
		{"logo.png", "image/png", "not really a png"},
		{"README", "text/plain; charset=utf-8", "plain text"},
		{"blob", "application/octet-stream", "\x00\x01\x02\x03"},
	}
	if len(files) != len(want) {
		t.Fatalf("files = %+v", files)
	}
	for i, w := range want {
		f := files[i]
		if f.FileName != w.name || f.ContentType != w.contentType || string(f.Data) != w.data {
			t.Errorf("file %d = %s %q %q, want %+v", i, f.FileName, f.ContentType, f.Data, w)
		}
	}
}

func TestReadFilesRefusesMoreThanTheUploadLimit(t *testing.T) {
	dir := t.TempDir()
	small := writeTestFile(t, dir, "a.pdf", "%PDF a")
	big := filepath.Join(dir, "big.pdf")
	f, err := os.Create(big)
	if err != nil {
		t.Fatal(err)
	}
	// A sparse file: the size check runs on the stat, before anything is read.
	if err := f.Truncate(MaxUploadBytes); err != nil {
		t.Fatal(err)
	}
	_ = f.Close()
	if _, err := ReadFiles([]string{big}); err != nil {
		t.Fatalf("exactly the limit: %v", err)
	}
	if _, err := ReadFiles([]string{small, big}); err == nil || !strings.Contains(err.Error(), "larger than 12 MB together") {
		t.Fatalf("err = %v", err)
	}
}

func TestReadFilesRefusesALineBreakInTheName(t *testing.T) {
	path := writeTestFile(t, t.TempDir(), "a\nb.pdf", "%PDF")
	if _, err := ReadFiles([]string{path}); err == nil || !strings.Contains(err.Error(), "line break") {
		t.Fatalf("err = %v", err)
	}
}
