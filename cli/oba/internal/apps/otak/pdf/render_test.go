package pdf

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestRenderFileInlinesThenRenders(t *testing.T) {
	clitest.StubWeasyPrint(t)
	dir := t.TempDir()
	writeImage(t, dir, "images/logo.png")
	src := filepath.Join(dir, "doc.html")
	if err := os.WriteFile(src, []byte(`<img src="images/logo.png"><p>Hallo</p>`), 0o600); err != nil {
		t.Fatal(err)
	}

	doc, err := RenderFile(context.Background(), src)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(doc.HTML), `src="data:image/png;base64,`) {
		t.Errorf("html not inlined: %s", doc.HTML)
	}
	pdf := string(doc.PDF)
	if !strings.HasPrefix(pdf, clitest.StubPDFPrefix) {
		t.Fatalf("pdf = %q", pdf)
	}
	if !strings.Contains(pdf, "base="+src+"\n") || !strings.HasSuffix(pdf, string(doc.HTML)) {
		t.Errorf("rendered something else than the inlined html with the source as base: %q", pdf)
	}
	if entries, _ := os.ReadDir(dir); len(entries) != 2 {
		t.Errorf("files written next to the source: %v", entries)
	}
}

func TestRenderWithoutWeasyPrint(t *testing.T) {
	clitest.NoWeasyPrint(t)
	_, err := Render(context.Background(), []byte("<p>x</p>"), "/tmp/x.html")
	if !errors.Is(err, ErrWeasyPrintMissing) || !strings.Contains(err.Error(), "brew install weasyprint") {
		t.Fatalf("err = %v", err)
	}
}

func TestRenderFailureShowsStderr(t *testing.T) {
	dir := t.TempDir()
	script := "#!/bin/sh\necho 'bad css at line 3' >&2\nexit 1\n"
	if err := os.WriteFile(filepath.Join(dir, "weasyprint"), []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", dir)
	_, err := Render(context.Background(), []byte("<p>x</p>"), "/tmp/x.html")
	if err == nil || !strings.Contains(err.Error(), "weasyprint failed") || !strings.Contains(err.Error(), "bad css at line 3") {
		t.Fatalf("err = %v", err)
	}
}

func TestRenderRefusesNonPDF(t *testing.T) {
	dir := t.TempDir()
	script := "#!/bin/sh\nprintf 'not a pdf' > \"$4\"\n"
	if err := os.WriteFile(filepath.Join(dir, "weasyprint"), []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", dir)
	_, err := Render(context.Background(), []byte("<p>x</p>"), "/tmp/x.html")
	if err == nil || !strings.Contains(err.Error(), "not a PDF") {
		t.Fatalf("err = %v", err)
	}
}
