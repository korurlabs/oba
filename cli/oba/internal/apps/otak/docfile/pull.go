package docfile

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"unicode"
)

// File is one file a pull writes: its extension and content.
type File struct {
	Ext  string
	Data []byte
}

// WriteFiles writes the files of one template into dir as <name><ext>, name made
// safe for a file name (fallback when nothing of it is left), and returns the paths written.
// Nothing is written when one of the files exists and overwrite is false, so that a pull never
// replaces a library source file by accident.
func WriteFiles(dir, name, fallback string, files []File, overwrite bool) ([]string, error) {
	if dir == "" {
		dir = "."
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, fmt.Errorf("failed to create %s: %w", dir, err)
	}
	base := SafeName(name)
	if base == "" {
		base = SafeName(fallback)
	}
	paths := make([]string, len(files))
	for i, f := range files {
		paths[i] = filepath.Join(dir, base+f.Ext)
		if overwrite {
			continue
		}
		if _, err := os.Stat(paths[i]); err == nil {
			return nil, fmt.Errorf("%s exists; pass --force to overwrite it", paths[i])
		} else if !errors.Is(err, fs.ErrNotExist) {
			return nil, fmt.Errorf("failed to check %s: %w", paths[i], err)
		}
	}
	for i, f := range files {
		if err := os.WriteFile(paths[i], f.Data, 0o644); err != nil {
			return paths[:i], fmt.Errorf("failed to write %s: %w", paths[i], err)
		}
	}
	return paths, nil
}

// SafeName turns a template name, which the API stores as any text, into a file
// name: letters, digits, '-', '_' and '.' are kept, every other run of characters becomes one
// '-', and leading dots and dashes are dropped, so that the name can neither leave the target
// directory nor hide the file.
func SafeName(name string) string {
	var b strings.Builder
	dash := false
	for _, r := range name {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '-' || r == '_' || r == '.' {
			b.WriteRune(r)
			dash = false
			continue
		}
		if !dash {
			b.WriteRune('-')
			dash = true
		}
	}
	return strings.Trim(b.String(), ".-")
}
