package attachment

import (
	"crypto/sha256"
	"fmt"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const (
	// MaxUploadBytes is the API's multipart limit (max-file-size and max-request-size, both
	// 12 MB): the most one file and all files of one upload may hold together.
	MaxUploadBytes = 12 * 1024 * 1024

	// sniffBytes is how much of a file http.DetectContentType reads.
	sniffBytes = 512

	defaultContentType = "application/octet-stream"
)

// ReadFiles reads every path for one upload, each with its base name and the media type the API
// will store, and checks it as the API would, so that a bad file fails before anything is sent:
// it must be a non-empty regular file without a line break in its name, the files together at
// most MaxUploadBytes, and no two of them with the same content, which the API refuses on one
// parent and so within one upload too. A leading ~ is the home directory, as a shell would expand
// it. Blank paths are skipped, so the result is empty when no path is given; the caller decides
// whether that is an error, since some records may be created without files.
func ReadFiles(paths []string) ([]client.AttachmentFile, error) {
	files := make([]client.AttachmentFile, 0, len(paths))
	seen := make(map[[sha256.Size]byte]string, len(paths))
	var total int64
	for _, raw := range paths {
		raw = strings.TrimSpace(raw)
		if raw == "" {
			continue
		}
		path, err := expandHome(raw)
		if err != nil {
			return nil, err
		}
		info, err := os.Stat(path)
		if err != nil {
			return nil, fmt.Errorf("failed to read %s: %w", path, err)
		}
		if !info.Mode().IsRegular() {
			return nil, fmt.Errorf("%s is not a regular file", path)
		}
		if info.Size() == 0 {
			return nil, fmt.Errorf("%s is empty; the API refuses an empty file", path)
		}
		name := filepath.Base(path)
		// The name goes into a multipart header, where a line break would start a new header.
		if strings.ContainsAny(name, "\r\n") {
			return nil, fmt.Errorf("%q has a line break in its file name, which cannot be sent", path)
		}
		total += info.Size()
		if total > MaxUploadBytes {
			return nil, fmt.Errorf("the files are larger than %d MB together; attach fewer at a time", MaxUploadBytes/(1024*1024))
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return nil, fmt.Errorf("failed to read %s: %w", path, err)
		}
		sum := sha256.Sum256(data)
		if previous, ok := seen[sum]; ok {
			return nil, fmt.Errorf("%s has the same content as %s; the API refuses the same content twice in one upload", path, previous)
		}
		seen[sum] = path
		files = append(files, client.AttachmentFile{FileName: name, ContentType: contentType(name, data), Data: data})
	}
	return files, nil
}

// expandHome resolves a leading ~, which reaches the CLI unexpanded when the path is quoted.
func expandHome(path string) (string, error) {
	if path != "~" && !strings.HasPrefix(path, "~/") {
		return path, nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("could not find the home directory for %q: %w", path, err)
	}
	return filepath.Join(home, strings.TrimPrefix(path, "~")), nil
}

// contentType names the media type by the extension first, which tells text/csv from
// text/plain, then by the content, and application/octet-stream when neither knows it.
func contentType(name string, data []byte) string {
	if byExt := mime.TypeByExtension(filepath.Ext(name)); byExt != "" {
		return byExt
	}
	if sniffed := http.DetectContentType(data[:min(len(data), sniffBytes)]); sniffed != "" {
		return sniffed
	}
	return defaultContentType
}
