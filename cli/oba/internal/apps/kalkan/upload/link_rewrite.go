package upload

import (
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// part is a piece of a body: literal text, or the link target of an asset (asset >= 0) that is
// filled in with its uploaded URL, or left as written when there is none yet.
type part struct {
	text     string
	asset    int
	original string
}

// Body returns the Markdown body with every asset link set to its URL in urls, keyed by
// Asset.Name; an asset without a URL keeps the link as written.
func (t Translation) Body(assets []Asset, urls map[string]string) string {
	var b strings.Builder
	for _, p := range t.parts {
		if p.asset < 0 {
			b.WriteString(p.text)
			continue
		}
		if link, ok := urls[assets[p.asset].Name]; ok {
			b.WriteString(link)
			continue
		}
		b.WriteString(p.original)
	}
	return b.String()
}

// inlineLink matches [text](target "title") and ![alt](target); group 1 is the target.
var inlineLink = regexp.MustCompile(`!?\[[^\]]*\]\(\s*(<[^>]*>|[^)\s]+)(?:\s+(?:"[^"]*"|'[^']*'|\([^)]*\)))?\s*\)`)

// referenceDefinition matches [label]: target; group 1 is the target.
var referenceDefinition = regexp.MustCompile(`^\s{0,3}\[[^\]]+\]:\s*(<[^>]*>|\S+)`)

// rewriteLinks turns links to Markdown files of the bundle into page paths (/root/a/b) and marks
// links to other local files inside the bundle as assets of the page. Links in code blocks,
// absolute URLs and links that leave the directory are kept as written.
func (r *reader) rewriteLinks(body, fileDir string, page *Page, assetIndex map[string]int) []part {
	var parts []part
	var text strings.Builder
	inFence := false
	for _, line := range strings.SplitAfter(body, "\n") {
		if isFence(strings.TrimSpace(line)) {
			inFence = !inFence
		}
		if inFence || isFence(strings.TrimSpace(line)) {
			text.WriteString(line)
			continue
		}
		matches := referenceDefinition.FindAllStringSubmatchIndex(line, 1)
		if matches == nil {
			matches = inlineLink.FindAllStringSubmatchIndex(line, -1)
		}
		last := 0
		for _, m := range matches {
			start, end := m[2], m[3]
			text.WriteString(line[last:start])
			last = end
			target := line[start:end]
			replacement, asset := r.resolve(target, fileDir, page, assetIndex)
			if asset < 0 {
				text.WriteString(replacement)
				continue
			}
			parts = append(parts, part{text: text.String(), asset: -1})
			text.Reset()
			parts = append(parts, part{asset: asset, original: target})
		}
		text.WriteString(line[last:])
	}
	return append(parts, part{text: text.String(), asset: -1})
}

// resolve returns the new link target and, for an asset, its index in page.Assets (else -1).
func (r *reader) resolve(target, fileDir string, page *Page, assetIndex map[string]int) (string, int) {
	raw := strings.TrimSuffix(strings.TrimPrefix(target, "<"), ">")
	if raw == "" || strings.HasPrefix(raw, "#") || strings.HasPrefix(raw, "/") || hasScheme(raw) {
		return target, -1
	}
	pathPart, fragment := raw, ""
	if i := strings.IndexAny(pathPart, "?#"); i >= 0 {
		pathPart, fragment = pathPart[:i], pathPart[i:]
	}
	if unescaped, err := url.PathUnescape(pathPart); err == nil {
		pathPart = unescaped
	}
	full := filepath.Join(fileDir, filepath.FromSlash(pathPart))
	rel, err := filepath.Rel(r.root, full)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return target, -1
	}
	if strings.EqualFold(filepath.Ext(full), ".md") {
		if pagePath, ok := r.fileToPage[full]; ok {
			if strings.HasPrefix(fragment, "?") {
				fragment = ""
			}
			return "/" + pagePath + fragment, -1
		}
		return target, -1
	}
	// A symbolic link to a directory inside the uploaded directory can lead outside it, which the
	// check on the written path cannot see; only the resolved path shows where the file lies.
	resolved, err := filepath.EvalSymlinks(full)
	if err != nil {
		return target, -1
	}
	if realRel, err := filepath.Rel(r.realRoot, resolved); err != nil || realRel == ".." || strings.HasPrefix(realRel, ".."+string(filepath.Separator)) {
		return target, -1
	}
	info, err := os.Lstat(full) // #nosec G703 -- full was checked above to lie inside the uploaded directory, also with symbolic links resolved
	if err != nil || !info.Mode().IsRegular() {
		return target, -1
	}
	return target, r.addAsset(full, info.Size(), page, assetIndex)
}

// hasScheme reports a URL with a scheme (https:, mailto:), which is never a local file.
func hasScheme(raw string) bool {
	colon := strings.Index(raw, ":")
	if colon <= 0 {
		return false
	}
	return !strings.ContainsAny(raw[:colon], "/\\")
}
