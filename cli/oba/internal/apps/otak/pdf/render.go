// Package pdf prepares an HTML document for Otak: it inlines the local images and renders the
// result to PDF with WeasyPrint, the engine the document library renders with, so that @page
// margin boxes and page breaks come out the same as in the library's own PDFs. The document
// template push uses it.
package pdf

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

const (
	// command is the WeasyPrint executable, looked up on PATH.
	command = "weasyprint"

	// stderrTail bounds how much of WeasyPrint's error output a failure shows.
	stderrTail = 2000
)

// pdfMagic starts every PDF file; the API checks it too.
var pdfMagic = []byte("%PDF-")

// ErrWeasyPrintMissing is the error when WeasyPrint is not on PATH.
var ErrWeasyPrintMissing = errors.New("weasyprint is not installed or not on your PATH; install it with 'brew install weasyprint' (macOS) or 'pip install weasyprint', then run the command again")

// Document is an HTML file made ready for upload: its HTML with every local image inlined, and
// the PDF rendered from exactly that HTML.
type Document struct {
	HTML []byte
	PDF  []byte
}

// RenderFile reads the HTML file at path, inlines its local images and renders the result to
// PDF. Nothing is written next to the source file.
func RenderFile(ctx context.Context, path string) (Document, error) {
	abs, err := filepath.Abs(path)
	if err != nil {
		return Document{}, fmt.Errorf("failed to resolve %s: %w", path, err)
	}
	source, err := os.ReadFile(abs)
	if err != nil {
		return Document{}, fmt.Errorf("failed to read %s: %w", path, err)
	}
	html, err := InlineImages(source, filepath.Dir(abs))
	if err != nil {
		return Document{}, err
	}
	pdf, err := Render(ctx, html, abs)
	if err != nil {
		return Document{}, err
	}
	return Document{HTML: html, PDF: pdf}, nil
}

// Render runs 'weasyprint --base-url <baseURL> <html> <pdf>' on a temporary copy of html and
// returns the PDF. baseURL resolves what the HTML still references relatively, such as a
// stylesheet; the source file's own path keeps those references as the library renders them.
func Render(ctx context.Context, html []byte, baseURL string) ([]byte, error) {
	executable, err := exec.LookPath(command)
	if err != nil {
		return nil, ErrWeasyPrintMissing
	}
	dir, err := os.MkdirTemp("", "oba-pdf-")
	if err != nil {
		return nil, fmt.Errorf("failed to create a temporary directory for the PDF: %w", err)
	}
	defer func() { _ = os.RemoveAll(dir) }()

	in := filepath.Join(dir, "document.html")
	out := filepath.Join(dir, "document.pdf")
	if err := os.WriteFile(in, html, 0o600); err != nil {
		return nil, fmt.Errorf("failed to write the HTML for WeasyPrint: %w", err)
	}

	var stderr bytes.Buffer
	cmd := exec.CommandContext(ctx, executable, "--base-url", baseURL, in, out)
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return nil, fmt.Errorf("weasyprint failed: %w%s", err, tail(stderr.String()))
	}
	pdf, err := os.ReadFile(out)
	if err != nil {
		return nil, fmt.Errorf("weasyprint wrote no PDF: %w", err)
	}
	if !bytes.HasPrefix(pdf, pdfMagic) {
		return nil, errors.New("weasyprint wrote a file that is not a PDF")
	}
	return pdf, nil
}

// tail is the end of WeasyPrint's error output, on its own lines, or "" when there is none.
func tail(stderr string) string {
	text := strings.TrimSpace(stderr)
	if text == "" {
		return ""
	}
	if len(text) > stderrTail {
		text = "..." + text[len(text)-stderrTail:]
	}
	return "\n" + text
}
