// Package bundle reads a directory of Markdown files as a tree of Kalkan pages. One layout serves
// both plain repo docs (a/b.md) and the seed export (a/b/index.tr.md, a/b/index.en.md), so neither
// has to be converted for the other.
//
// Layout: a/b.md is page a/b in the default locale; a/b.tr.md and a/b.en.md set the locale;
// a/index[.lang].md or a/README[.lang].md is page a; a directory that holds Markdown but no index
// file becomes a page titled by its slug with an empty body. Hidden entries, symbolic links and
// directories named handoffs are not read, so nothing outside the directory and no working notes
// reach Kalkan.
package bundle

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"gopkg.in/yaml.v3"
)

// MaxDepth is the deepest page path the server accepts.
const MaxDepth = 10

// MaxAssetBytes mirrors the server's multipart limit of 12 MB per file and per request; a larger
// asset fails its page before the upload is tried.
const MaxAssetBytes int64 = 12 * 1024 * 1024

// slugPattern is the server's slug rule.
var slugPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$`)

// ValidSlug reports whether s is a slug the server accepts.
func ValidSlug(s string) bool {
	return slugPattern.MatchString(s)
}

// skippedDir is never read: handoffs are working notes between agents, not documentation.
const skippedDir = "handoffs"

// Asset is a local file a page links to by a relative path. It is uploaded to the page under
// Name, the first 12 hex digits of its SHA-256 and its base name, so a changed file gets a new
// name and an unchanged one is found again on the next run.
type Asset struct {
	Source string
	Name   string
	Size   int64
}

// part is a piece of a body: literal text, or the link target of an asset (Asset >= 0) that is
// filled in with its uploaded URL, or left as written when there is none yet.
type part struct {
	text     string
	asset    int
	original string
}

// Translation is the title and body of a page in one locale.
type Translation struct {
	Locale string
	Title  string
	parts  []part
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

// Page is one page of the bundle. Position and Compliance are nil when no front matter sets them,
// so the stored values are kept. DirOnly marks a directory without an index file: it is created
// when missing and otherwise left as it is in Kalkan. AssetErr is a local problem with one of the
// page's assets, which fails that page only.
type Page struct {
	Path         string
	Position     *int
	Compliance   *bool
	Translations []Translation
	Assets       []Asset
	DirOnly      bool
	AssetErr     error
}

// Bundle is the pages of one directory under one target path, parents before children.
type Bundle struct {
	Dir    string
	Target string
	Pages  []Page
}

// frontMatter is the optional YAML block at the top of a file. Other keys are ignored, since
// repo docs may carry their own.
type frontMatter struct {
	Title      *string `yaml:"title"`
	Position   *int    `yaml:"position"`
	Compliance *bool   `yaml:"compliance"`
}

// source is a page while the directory is read: its Markdown file per locale.
type source struct {
	path  string
	files map[string]string
}

type reader struct {
	root          string
	realRoot      string // root with every symbolic link resolved, to check where a linked file really lies
	target        string
	defaultLocale string
	pages         map[string]*source
	fileToPage    map[string]string
}

// Read reads dir as the pages under target, a path of slugs. defaultLocale is the API locale
// (TR or EN) of files without a language suffix. Every error is local and found before any call:
// a missing directory, a segment that is not a slug, a path deeper than MaxDepth, two files for
// one page and locale, front matter that does not parse or disagrees across locales.
func Read(dir, target, defaultLocale string) (*Bundle, error) {
	root, err := filepath.Abs(dir)
	if err != nil {
		return nil, fmt.Errorf("resolve %s: %w", dir, err)
	}
	info, err := os.Stat(root)
	if err != nil {
		return nil, fmt.Errorf("the directory %s cannot be read: %w", dir, err)
	}
	if !info.IsDir() {
		return nil, fmt.Errorf("%s is not a directory", dir)
	}
	realRoot, err := filepath.EvalSymlinks(root)
	if err != nil {
		return nil, fmt.Errorf("resolve %s: %w", dir, err)
	}
	r := &reader{
		root:          root,
		realRoot:      realRoot,
		target:        target,
		defaultLocale: defaultLocale,
		pages:         make(map[string]*source),
		fileToPage:    make(map[string]string),
	}
	hasMarkdown, err := r.walk(root, target)
	if err != nil {
		return nil, err
	}
	if !hasMarkdown {
		return nil, fmt.Errorf("the directory %s holds no Markdown files", dir)
	}

	paths := make([]string, 0, len(r.pages))
	for path := range r.pages {
		paths = append(paths, path)
	}
	sort.Slice(paths, func(i, j int) bool { return parentFirst(paths[i], paths[j]) })

	bundle := &Bundle{Dir: root, Target: target}
	for _, path := range paths {
		page, err := r.build(r.pages[path])
		if err != nil {
			return nil, err
		}
		bundle.Pages = append(bundle.Pages, page)
	}
	return bundle, nil
}

// parentFirst orders paths segment by segment, so a page comes right before its subtree.
func parentFirst(a, b string) bool {
	as, bs := strings.Split(a, "/"), strings.Split(b, "/")
	for i := 0; i < len(as) && i < len(bs); i++ {
		if as[i] != bs[i] {
			return as[i] < bs[i]
		}
	}
	return len(as) < len(bs)
}

// walk reads one directory as the page at pagePath and reports whether it or a subdirectory holds
// Markdown; a directory without any is not a page (it may hold the assets of its siblings).
func (r *reader) walk(dir, pagePath string) (bool, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return false, fmt.Errorf("read %s: %w", dir, err)
	}
	hasMarkdown := false
	for _, entry := range entries {
		name := entry.Name()
		if strings.HasPrefix(name, ".") || entry.Type()&os.ModeSymlink != 0 {
			continue
		}
		full := filepath.Join(dir, name)
		if entry.IsDir() {
			if name == skippedDir {
				continue
			}
			childPath := pagePath + "/" + name
			childHas, err := r.walk(full, childPath)
			if err != nil {
				return false, err
			}
			if childHas {
				if err := r.checkPath(childPath, full); err != nil {
					return false, err
				}
				hasMarkdown = true
			}
			continue
		}
		if !entry.Type().IsRegular() || !strings.EqualFold(filepath.Ext(name), ".md") {
			continue
		}
		stem := strings.TrimSuffix(name, filepath.Ext(name))
		locale := r.defaultLocale
		if ext := strings.ToLower(filepath.Ext(stem)); ext == ".tr" || ext == ".en" {
			locale = strings.ToUpper(ext[1:])
			stem = strings.TrimSuffix(stem, filepath.Ext(stem))
		}
		path := pagePath
		if !strings.EqualFold(stem, "index") && !strings.EqualFold(stem, "readme") {
			path = pagePath + "/" + stem
			if err := r.checkPath(path, full); err != nil {
				return false, err
			}
		}
		src := r.source(path)
		if previous, taken := src.files[locale]; taken {
			return false, fmt.Errorf("%s and %s are both page %s in %s; keep one", previous, full, path, locale)
		}
		src.files[locale] = full
		r.fileToPage[full] = path
		hasMarkdown = true
	}
	if hasMarkdown {
		if err := r.checkPath(pagePath, dir); err != nil {
			return false, err
		}
		r.source(pagePath)
	}
	return hasMarkdown, nil
}

func (r *reader) source(path string) *source {
	src, ok := r.pages[path]
	if !ok {
		src = &source{path: path, files: make(map[string]string)}
		r.pages[path] = src
	}
	return src
}

// checkPath fails a page path the server would refuse, naming the file it came from.
func (r *reader) checkPath(path, file string) error {
	segments := strings.Split(path, "/")
	if len(segments) > MaxDepth {
		return fmt.Errorf("%s would be page %s, deeper than %d pages", file, path, MaxDepth)
	}
	for _, segment := range segments {
		if !ValidSlug(segment) {
			return fmt.Errorf("%s would be page %s, but %q is not a valid slug: start with a letter or digit, then up to 99 letters, digits, '.', '_' or '-'", file, path, segment)
		}
	}
	return nil
}

func (r *reader) build(src *source) (Page, error) {
	page := Page{Path: src.path}
	slug := src.path[strings.LastIndex(src.path, "/")+1:]
	if len(src.files) == 0 {
		page.DirOnly = true
		page.Translations = []Translation{{Locale: r.defaultLocale, Title: slug, parts: []part{{text: "", asset: -1}}}}
		return page, nil
	}
	locales := make([]string, 0, len(src.files))
	for locale := range src.files {
		locales = append(locales, locale)
	}
	sort.Strings(locales)
	assetIndex := make(map[string]int)
	for _, locale := range locales {
		file := src.files[locale]
		data, err := os.ReadFile(file) // #nosec G304 -- a file inside the directory the user pushes
		if err != nil {
			return Page{}, fmt.Errorf("read %s: %w", file, err)
		}
		fm, body, err := splitFrontMatter(string(data))
		if err != nil {
			return Page{}, fmt.Errorf("%s: front matter: %w", file, err)
		}
		if page.Position, err = agree("position", page.Position, fm.Position, file); err != nil {
			return Page{}, err
		}
		if page.Compliance, err = agree("compliance", page.Compliance, fm.Compliance, file); err != nil {
			return Page{}, err
		}
		if fm.Position != nil && *fm.Position < 0 {
			return Page{}, fmt.Errorf("%s: position must be zero or more", file)
		}
		title := ""
		if fm.Title != nil {
			title = strings.TrimSpace(*fm.Title)
		}
		if title == "" {
			title, body = takeHeading(body)
		}
		if title == "" {
			title = slug
		}
		parts := r.rewriteLinks(body, filepath.Dir(file), &page, assetIndex)
		page.Translations = append(page.Translations, Translation{Locale: locale, Title: title, parts: parts})
	}
	return page, nil
}

// agree keeps a page field that every locale file must set alike.
func agree[T comparable](field string, current, next *T, file string) (*T, error) {
	if next == nil {
		return current, nil
	}
	if current != nil && *current != *next {
		return nil, fmt.Errorf("%s sets %s to %v, another language of the page to %v; they must agree", file, field, *next, *current)
	}
	return next, nil
}

// splitFrontMatter takes a leading YAML block between two --- lines off the content.
func splitFrontMatter(content string) (frontMatter, string, error) {
	var fm frontMatter
	normalized := strings.TrimPrefix(content, "\ufeff")
	if !strings.HasPrefix(normalized, "---\n") && !strings.HasPrefix(normalized, "---\r\n") {
		return fm, content, nil
	}
	rest := normalized[strings.Index(normalized, "\n")+1:]
	offset := 0
	for {
		end := strings.Index(rest[offset:], "\n")
		line := rest[offset:]
		if end >= 0 {
			line = rest[offset : offset+end]
		}
		if trimmed := strings.TrimRight(line, "\r"); trimmed == "---" || trimmed == "..." {
			block := rest[:offset]
			body := ""
			if end >= 0 {
				body = rest[offset+end+1:]
			}
			decoder := yaml.NewDecoder(strings.NewReader(block))
			if err := decoder.Decode(&fm); err != nil && !errors.Is(err, io.EOF) {
				return fm, "", err
			}
			return fm, body, nil
		}
		if end < 0 {
			return fm, "", errors.New("no closing --- line")
		}
		offset += end + 1
	}
}

// takeHeading takes the first level-one heading outside a code block out of the body and
// returns its text as the title.
func takeHeading(body string) (string, string) {
	lines := strings.SplitAfter(body, "\n")
	inFence := false
	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if isFence(trimmed) {
			inFence = !inFence
			continue
		}
		if inFence || !strings.HasPrefix(trimmed, "# ") {
			continue
		}
		title := strings.TrimSpace(strings.TrimRight(strings.TrimPrefix(trimmed, "# "), "#"))
		rest := strings.Join(lines[:i], "") + strings.Join(lines[i+1:], "")
		return title, strings.TrimLeft(rest, "\r\n")
	}
	return "", body
}

func isFence(trimmed string) bool {
	return strings.HasPrefix(trimmed, "```") || strings.HasPrefix(trimmed, "~~~")
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
	// A symbolic link to a directory inside the pushed directory can lead outside it, which the
	// check on the written path cannot see; only the resolved path shows where the file lies.
	resolved, err := filepath.EvalSymlinks(full)
	if err != nil {
		return target, -1
	}
	if realRel, err := filepath.Rel(r.realRoot, resolved); err != nil || realRel == ".." || strings.HasPrefix(realRel, ".."+string(filepath.Separator)) {
		return target, -1
	}
	info, err := os.Lstat(full) // #nosec G703 -- full was checked above to lie inside the pushed directory, also with symbolic links resolved
	if err != nil || !info.Mode().IsRegular() {
		return target, -1
	}
	return target, r.addAsset(full, info.Size(), page, assetIndex)
}

// addAsset hashes one linked file and adds it to the page once; a file the server would refuse
// sets the page's AssetErr instead.
func (r *reader) addAsset(full string, size int64, page *Page, assetIndex map[string]int) int {
	if index, ok := assetIndex[full]; ok {
		return index
	}
	name := filepath.Base(full)
	var problem error
	switch {
	case size == 0:
		problem = fmt.Errorf("the asset %s is empty; the server refuses empty files", full)
	case size > MaxAssetBytes:
		problem = fmt.Errorf("the asset %s is larger than the 12 MB upload limit", full)
	case strings.ContainsAny(name, "\r\n\""):
		problem = fmt.Errorf("the asset %s has a quote or line break in its name, which cannot be uploaded", full)
	}
	digest := ""
	if problem == nil {
		var err error
		digest, err = hashFile(full)
		if err != nil {
			problem = err
		}
	}
	if problem != nil {
		if page.AssetErr == nil {
			page.AssetErr = problem
		}
		assetIndex[full] = -1
		return -1
	}
	page.Assets = append(page.Assets, Asset{Source: full, Name: digest[:12] + "-" + name, Size: size})
	assetIndex[full] = len(page.Assets) - 1
	return len(page.Assets) - 1
}

func hashFile(path string) (string, error) {
	data, err := os.ReadFile(path) // #nosec G304 G703 -- an asset inside the directory the user pushes
	if err != nil {
		return "", fmt.Errorf("read the asset %s: %w", path, err)
	}
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:]), nil
}

// hasScheme reports a URL with a scheme (https:, mailto:), which is never a local file.
func hasScheme(raw string) bool {
	colon := strings.Index(raw, ":")
	if colon <= 0 {
		return false
	}
	return !strings.ContainsAny(raw[:colon], "/\\")
}
