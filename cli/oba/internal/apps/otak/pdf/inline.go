package pdf

import (
	"encoding/base64"
	"fmt"
	"mime"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// imgSrc matches the src attribute of an <img> tag, quoted with " or '. Group 1 is everything
// up to the value; group 2 is a double-quoted value, group 3 a single-quoted one. src must follow
// whitespace, so that data-src and the like are left alone.
var imgSrc = regexp.MustCompile(`(?is)(<img\b[^>]*?\ssrc\s*=\s*)(?:"([^"]*)"|'([^']*)')`)

// InlineImages replaces the source of every <img> that names a local file, relative to dir or
// absolute, with a data URI of that file. The uploaded HTML then renders the same anywhere,
// without the library's images folder next to it. Sources with a scheme (https:, data:, ...) or
// starting with // stay as they are. A local image that cannot be read is an error, so that no
// document is uploaded with a broken picture.
func InlineImages(html []byte, dir string) ([]byte, error) {
	cache := map[string]string{}
	var firstErr error
	out := imgSrc.ReplaceAllFunc(html, func(match []byte) []byte {
		if firstErr != nil {
			return match
		}
		groups := imgSrc.FindSubmatchIndex(match)
		prefix := string(match[groups[2]:groups[3]])
		quote, value := `"`, ""
		if groups[4] >= 0 {
			value = string(match[groups[4]:groups[5]])
		} else {
			quote, value = "'", string(match[groups[6]:groups[7]])
		}
		path, ok := localPath(value, dir)
		if !ok {
			return match
		}
		uri, cached := cache[path]
		if !cached {
			encoded, err := dataURI(path)
			if err != nil {
				firstErr = fmt.Errorf("image %q: %w", value, err)
				return match
			}
			uri = encoded
			cache[path] = uri
		}
		return []byte(prefix + quote + uri + quote)
	})
	if firstErr != nil {
		return nil, firstErr
	}
	return out, nil
}

// localPath turns an image source into a file path, or reports false for a source that is not
// a local file. A file: URL counts as local.
func localPath(src, dir string) (string, bool) {
	src = strings.TrimSpace(src)
	if src == "" || strings.HasPrefix(src, "//") || strings.HasPrefix(src, "#") {
		return "", false
	}
	parsed, err := url.Parse(src)
	if err != nil {
		return "", false
	}
	switch parsed.Scheme {
	case "":
	case "file":
		return filepath.FromSlash(parsed.Path), true
	default:
		return "", false
	}
	// The path is URL-encoded in HTML (a space is %20); query and fragment are not part of the file.
	p := filepath.FromSlash(parsed.Path)
	if p == "" {
		return "", false
	}
	if filepath.IsAbs(p) {
		return p, true
	}
	return filepath.Join(dir, p), true
}

// dataURI reads the file and encodes it as a base64 data URI with the media type of its
// extension, or the one sniffed from its content.
func dataURI(path string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	mediaType := mime.TypeByExtension(strings.ToLower(filepath.Ext(path)))
	if mediaType == "" {
		mediaType = http.DetectContentType(data)
	}
	if i := strings.Index(mediaType, ";"); i >= 0 {
		mediaType = strings.TrimSpace(mediaType[:i])
	}
	return "data:" + mediaType + ";base64," + base64.StdEncoding.EncodeToString(data), nil
}
