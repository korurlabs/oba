package clitest

import (
	"os"
	"path/filepath"
	"testing"
)

// StubPDFPrefix starts every PDF the stub WeasyPrint writes. The stub then writes the base URL
// line and the HTML it was given, so that a test can see what was rendered.
const StubPDFPrefix = "%PDF-1.7 stub\n"

// stubWeasyPrint stands in for 'weasyprint --base-url <base> <in> <out>'.
const stubWeasyPrint = `#!/bin/sh
{ printf '%%PDF-1.7 stub\n'; printf 'base=%s\n' "$2"; cat "$3"; } > "$4"
`

// StubWeasyPrint puts a stub weasyprint first on PATH for the test, so that rendering needs no
// WeasyPrint installed.
func StubWeasyPrint(t *testing.T) {
	t.Helper()
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "weasyprint"), []byte(stubWeasyPrint), 0o755); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
}

// NoWeasyPrint leaves weasyprint off PATH for the test.
func NoWeasyPrint(t *testing.T) {
	t.Helper()
	t.Setenv("PATH", t.TempDir())
}
