package upload

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// skippedDir is never read: handoffs are working notes between agents, not documentation.
const skippedDir = "handoffs"

// Translation is the title and body of a page in one locale.
type Translation struct {
	Locale string
	Title  string
	parts  []part
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

// ReadBundle reads dir as the pages under target, a path of slugs. defaultLocale is the API locale
// (TR or EN) of files without a language suffix.
//
// One layout serves both plain repo docs (a/b.md) and the seed export (a/b/index.tr.md,
// a/b/index.en.md), so neither has to be converted for the other: a/b.md is page a/b in the
// default locale; a/b.tr.md and a/b.en.md set the locale; a/index[.lang].md or a/README[.lang].md
// is page a; a directory that holds Markdown but no index file becomes a page titled by its slug
// with an empty body. Hidden entries, symbolic links and directories named handoffs are not read,
// so nothing outside the directory and no working notes reach Kalkan.
//
// Every error is local and found before any call:
// a missing directory, a segment that is not a slug, a path deeper than client.KalkanMaxPageDepth,
// two files for one page and locale, front matter that does not parse or disagrees across locales.
func ReadBundle(dir, target, defaultLocale string) (*Bundle, error) {
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
	if len(segments) > client.KalkanMaxPageDepth {
		return fmt.Errorf("%s would be page %s, deeper than %d pages", file, path, client.KalkanMaxPageDepth)
	}
	for _, segment := range segments {
		if !client.ValidKalkanSlug(segment) {
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
		data, err := os.ReadFile(file) // #nosec G304 -- a file inside the directory the user uploads
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
