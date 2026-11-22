package docfile

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// DownloadHTML reads the stored HTML of a document or template from the signed URL the API
// returned. what names it in messages ("document <id>"); uploadHint is the command that gives it
// files, named when it has none yet.
func DownloadHTML(htmlURL *string, what, uploadHint string) ([]byte, error) {
	if htmlURL == nil || *htmlURL == "" {
		return nil, fmt.Errorf("%s has no files yet; upload its HTML with '%s' first", what, uploadHint)
	}
	html, err := client.Download(*htmlURL)
	if err != nil {
		return nil, fmt.Errorf("failed to download the HTML of %s: %w", what, err)
	}
	return html, nil
}

// HTMLPath is the file a download writes: output when given, else <name>.html in the current
// directory, name made safe for a file name (fallback when nothing of it is left).
func HTMLPath(output, name, fallback string) string {
	if output != "" {
		return output
	}
	base := SafeName(name)
	if base == "" {
		base = SafeName(fallback)
	}
	return base + ExtHTML
}

// WriteFile writes data to path, creating its directory. An existing file is not replaced unless
// overwrite is true, so that a download never replaces a library source file by accident.
func WriteFile(path string, data []byte, overwrite bool) error {
	if !overwrite {
		if _, err := os.Stat(path); err == nil {
			return fmt.Errorf("%s exists; pass --force to overwrite it", path)
		} else if !errors.Is(err, fs.ErrNotExist) {
			return fmt.Errorf("failed to check %s: %w", path, err)
		}
	}
	if dir := filepath.Dir(path); dir != "." {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return fmt.Errorf("failed to create %s: %w", dir, err)
		}
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		return fmt.Errorf("failed to write %s: %w", path, err)
	}
	return nil
}
