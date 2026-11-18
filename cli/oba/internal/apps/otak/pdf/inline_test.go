package pdf

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

var pngBytes = []byte("\x89PNG\r\n\x1a\nfake")

func writeImage(t *testing.T, dir, name string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, pngBytes, 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestInlineImagesLocalSources(t *testing.T) {
	root := t.TempDir()
	docDir := filepath.Join(root, "legal", "nda")
	writeImage(t, root, "images/logo korur.png")
	abs := writeImage(t, root, "abs.png")
	want := "data:image/png;base64," + base64.StdEncoding.EncodeToString(pngBytes)

	html := `<html><body>` +
		`<img class="b" src="../../images/logo%20korur.png" alt="x">` +
		`<IMG SRC='` + abs + `'>` +
		`<img src="file://` + filepath.ToSlash(abs) + `">` +
		`<img src="../../images/logo%20korur.png?v=2#top">` +
		`</body></html>`
	out, err := InlineImages([]byte(html), docDir)
	if err != nil {
		t.Fatal(err)
	}
	got := string(out)
	if n := strings.Count(got, want); n != 4 {
		t.Errorf("want 4 data URIs, got %d in %s", n, got)
	}
	if !strings.Contains(got, `<img class="b" src="`+want+`" alt="x">`) || !strings.Contains(got, `<IMG SRC='`+want+`'>`) {
		t.Errorf("quotes or attributes not kept: %s", got)
	}
}

func TestInlineImagesLeavesOtherSources(t *testing.T) {
	html := `<img src="https://korur.com/logo.png"><img src="data:image/png;base64,AAAA">` +
		`<img src="//cdn.example/x.png"><img data-src="missing.png"><img src="">` +
		`<link href="style.css" rel="stylesheet">`
	out, err := InlineImages([]byte(html), t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if string(out) != html {
		t.Errorf("changed:\n%s", out)
	}
}

func TestInlineImagesMissingFile(t *testing.T) {
	_, err := InlineImages([]byte(`<img src="images/gone.png">`), t.TempDir())
	if err == nil || !strings.Contains(err.Error(), `image "images/gone.png"`) {
		t.Fatalf("err = %v", err)
	}
}

func TestInlineImagesSvgMediaType(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "a.svg"), []byte("<svg/>"), 0o600); err != nil {
		t.Fatal(err)
	}
	out, err := InlineImages([]byte(`<img src="a.svg">`), dir)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(out), `src="data:image/svg+xml;base64,`) {
		t.Errorf("out = %s", out)
	}
}
