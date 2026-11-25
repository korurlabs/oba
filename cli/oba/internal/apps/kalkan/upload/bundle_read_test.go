package upload

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLayoutPlainFilesAndIndexFiles(t *testing.T) {
	dir := tree(t, map[string]string{
		"index.md":           "# Root title\n\nroot body\n",
		"guide.md":           "# Guide\n\nbody\n",
		"guide.tr.md":        "# Kilavuz\n\ngovde\n",
		"sub/README.md":      "# Sub\n",
		"sub/deep.md":        "no heading\n",
		"seed/x/index.tr.md": "# X tr\n",
		"seed/x/index.en.md": "# X en\n",
	})
	b := mustRead(t, dir, "eng/docs")
	want := []string{"eng/docs", "eng/docs/guide", "eng/docs/seed", "eng/docs/seed/x", "eng/docs/sub", "eng/docs/sub/deep"}
	if got := pagePaths(b); strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("paths = %v, want %v (parents first)", got, want)
	}
	if p := findPage(t, b, "eng/docs"); titleOf(p, "EN") != "Root title" || p.DirOnly {
		t.Fatalf("root page %+v", p)
	}
	guide := findPage(t, b, "eng/docs/guide")
	if len(guide.Translations) != 2 || titleOf(guide, "EN") != "Guide" || titleOf(guide, "TR") != "Kilavuz" {
		t.Fatalf("guide translations %+v", guide.Translations)
	}
	if p := findPage(t, b, "eng/docs/sub/deep"); titleOf(p, "EN") != "deep" {
		t.Fatalf("title falls back to the slug, got %q", titleOf(p, "EN"))
	}
	seed := findPage(t, b, "eng/docs/seed")
	if !seed.DirOnly || titleOf(seed, "EN") != "seed" || seed.Translations[0].Body(nil, nil) != "" {
		t.Fatalf("directory without index: %+v", seed)
	}
	x := findPage(t, b, "eng/docs/seed/x")
	if titleOf(x, "TR") != "X tr" || titleOf(x, "EN") != "X en" {
		t.Fatalf("index bundle %+v", x.Translations)
	}
}

func TestDefaultLocaleForUnsuffixedFiles(t *testing.T) {
	dir := tree(t, map[string]string{"a.md": "# A\n"})
	b, err := ReadBundle(dir, "root", "TR")
	if err != nil {
		t.Fatal(err)
	}
	if got := titleOf(findPage(t, b, "root/a"), "TR"); got != "A" {
		t.Fatalf("title = %q, want A in TR", got)
	}
}

func TestSkippedEntries(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md":          "# A\n",
		".hidden/x.md":  "# H\n",
		".dot.md":       "# D\n",
		"handoffs/y.md": "# Y\n",
		"notes.txt":     "x",
	})
	if err := os.Symlink(filepath.Join(dir, "a.md"), filepath.Join(dir, "link.md")); err != nil {
		t.Skip("symlinks unavailable")
	}
	b := mustRead(t, dir, "r")
	if got := pagePaths(b); len(got) != 2 || got[0] != "r" || got[1] != "r/a" {
		t.Fatalf("paths = %v, want only r and r/a", got)
	}
}

func TestDirectoryWithoutMarkdownIsNotAPage(t *testing.T) {
	dir := tree(t, map[string]string{"a.md": "# A\n", "img/logo.png": "png"})
	b := mustRead(t, dir, "r")
	for _, p := range pagePaths(b) {
		if strings.Contains(p, "img") {
			t.Fatalf("img became a page: %v", pagePaths(b))
		}
	}
}

func TestReadErrors(t *testing.T) {
	long := strings.Repeat("a", 101)
	cases := map[string]struct {
		files  map[string]string
		target string
		want   string
	}{
		"empty dir":         {map[string]string{"x.txt": "x"}, "r", "no Markdown files"},
		"invalid slug":      {map[string]string{"bad name.md": "x"}, "r", "not a valid slug"},
		"slug too long":     {map[string]string{long + ".md": "x"}, "r", "not a valid slug"},
		"leading dash":      {map[string]string{"-a.md": "x"}, "r", "not a valid slug"},
		"two for one":       {map[string]string{"a.md": "x", "a/index.md": "y"}, "r", "both page r/a"},
		"too deep":          {map[string]string{"a.md": "x"}, "a/b/c/d/e/f/g/h/i/j", "deeper than 10"},
		"bad front":         {map[string]string{"a.md": "---\ntitle: [\n---\nbody"}, "r", "front matter"},
		"unclosed front":    {map[string]string{"a.md": "---\ntitle: x\nbody"}, "r", "no closing"},
		"position clash":    {map[string]string{"a.md": "---\nposition: 1\n---\n", "a.tr.md": "---\nposition: 2\n---\n"}, "r", "must agree"},
		"compliance clash":  {map[string]string{"a.md": "---\ncompliance: true\n---\n", "a.tr.md": "---\ncompliance: false\n---\n"}, "r", "must agree"},
		"negative position": {map[string]string{"a.md": "---\nposition: -1\n---\n"}, "r", "zero or more"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			dir := tree(t, tc.files)
			_, err := ReadBundle(dir, tc.target, "EN")
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("ReadBundle() = %v, want %q", err, tc.want)
			}
		})
	}
	if _, err := ReadBundle(filepath.Join(t.TempDir(), "missing"), "r", "EN"); err == nil {
		t.Fatal("a missing directory must fail")
	}
	file := tree(t, map[string]string{"a.md": "x"})
	if _, err := ReadBundle(filepath.Join(file, "a.md"), "r", "EN"); err == nil || !strings.Contains(err.Error(), "not a directory") {
		t.Fatalf("ReadBundle(file) = %v", err)
	}
}

func TestDepthLimitCountsDirectories(t *testing.T) {
	name := "a/b/c/d/e/f/g/h/i/j/k.md" // r + 10 segments = 11
	dir := tree(t, map[string]string{name: "x"})
	if _, err := ReadBundle(dir, "r", "EN"); err == nil || !strings.Contains(err.Error(), "deeper than 10") {
		t.Fatalf("ReadBundle() = %v", err)
	}
	ok := tree(t, map[string]string{"a/b/c/d/e/f/g/h/i.md": "x"}) // r + 9 = 10
	if _, err := ReadBundle(ok, "r", "EN"); err != nil {
		t.Fatalf("ten levels must pass: %v", err)
	}
}
