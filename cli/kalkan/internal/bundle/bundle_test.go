package bundle

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// tree writes files (relative path to content) under a new directory and returns it.
func tree(t *testing.T, files map[string]string) string {
	t.Helper()
	dir := t.TempDir()
	for name, content := range files {
		full := filepath.Join(dir, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(full), 0o750); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(full, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return dir
}

func read(t *testing.T, dir, target string) *Bundle {
	t.Helper()
	b, err := Read(dir, target, "EN")
	if err != nil {
		t.Fatalf("Read() = %v", err)
	}
	return b
}

func paths(b *Bundle) []string {
	var out []string
	for _, p := range b.Pages {
		out = append(out, p.Path)
	}
	return out
}

func find(t *testing.T, b *Bundle, path string) Page {
	t.Helper()
	for _, p := range b.Pages {
		if p.Path == path {
			return p
		}
	}
	t.Fatalf("no page %s in %v", path, paths(b))
	return Page{}
}

func titleOf(p Page, locale string) string {
	for _, tr := range p.Translations {
		if tr.Locale == locale {
			return tr.Title
		}
	}
	return "<none>"
}

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
	b := read(t, dir, "eng/docs")
	want := []string{"eng/docs", "eng/docs/guide", "eng/docs/seed", "eng/docs/seed/x", "eng/docs/sub", "eng/docs/sub/deep"}
	if got := paths(b); strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("paths = %v, want %v (parents first)", got, want)
	}
	if p := find(t, b, "eng/docs"); titleOf(p, "EN") != "Root title" || p.DirOnly {
		t.Fatalf("root page %+v", p)
	}
	guide := find(t, b, "eng/docs/guide")
	if len(guide.Translations) != 2 || titleOf(guide, "EN") != "Guide" || titleOf(guide, "TR") != "Kilavuz" {
		t.Fatalf("guide translations %+v", guide.Translations)
	}
	if p := find(t, b, "eng/docs/sub/deep"); titleOf(p, "EN") != "deep" {
		t.Fatalf("title falls back to the slug, got %q", titleOf(p, "EN"))
	}
	seed := find(t, b, "eng/docs/seed")
	if !seed.DirOnly || titleOf(seed, "EN") != "seed" || seed.Translations[0].Body(nil, nil) != "" {
		t.Fatalf("directory without index: %+v", seed)
	}
	x := find(t, b, "eng/docs/seed/x")
	if titleOf(x, "TR") != "X tr" || titleOf(x, "EN") != "X en" {
		t.Fatalf("index bundle %+v", x.Translations)
	}
}

func TestDefaultLocaleForUnsuffixedFiles(t *testing.T) {
	dir := tree(t, map[string]string{"a.md": "# A\n"})
	b, err := Read(dir, "root", "TR")
	if err != nil {
		t.Fatal(err)
	}
	if got := titleOf(find(t, b, "root/a"), "TR"); got != "A" {
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
	b := read(t, dir, "r")
	if got := paths(b); len(got) != 2 || got[0] != "r" || got[1] != "r/a" {
		t.Fatalf("paths = %v, want only r and r/a", got)
	}
}

func TestDirectoryWithoutMarkdownIsNotAPage(t *testing.T) {
	dir := tree(t, map[string]string{"a.md": "# A\n", "img/logo.png": "png"})
	b := read(t, dir, "r")
	for _, p := range paths(b) {
		if strings.Contains(p, "img") {
			t.Fatalf("img became a page: %v", paths(b))
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
			_, err := Read(dir, tc.target, "EN")
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("Read() = %v, want %q", err, tc.want)
			}
		})
	}
	if _, err := Read(filepath.Join(t.TempDir(), "missing"), "r", "EN"); err == nil {
		t.Fatal("a missing directory must fail")
	}
	file := tree(t, map[string]string{"a.md": "x"})
	if _, err := Read(filepath.Join(file, "a.md"), "r", "EN"); err == nil || !strings.Contains(err.Error(), "not a directory") {
		t.Fatalf("Read(file) = %v", err)
	}
}

func TestDepthLimitCountsDirectories(t *testing.T) {
	name := "a/b/c/d/e/f/g/h/i/j/k.md" // r + 10 segments = 11
	dir := tree(t, map[string]string{name: "x"})
	if _, err := Read(dir, "r", "EN"); err == nil || !strings.Contains(err.Error(), "deeper than 10") {
		t.Fatalf("Read() = %v", err)
	}
	ok := tree(t, map[string]string{"a/b/c/d/e/f/g/h/i.md": "x"}) // r + 9 = 10
	if _, err := Read(ok, "r", "EN"); err != nil {
		t.Fatalf("ten levels must pass: %v", err)
	}
}

func TestValidSlug(t *testing.T) {
	good := []string{"a", "A1", "a.b_c-d", "0", strings.Repeat("a", 100)}
	bad := []string{"", ".a", "_a", "-a", "a b", "a/b", "ü", strings.Repeat("a", 101)}
	for _, s := range good {
		if !ValidSlug(s) {
			t.Errorf("ValidSlug(%q) = false", s)
		}
	}
	for _, s := range bad {
		if ValidSlug(s) {
			t.Errorf("ValidSlug(%q) = true", s)
		}
	}
}

func TestFrontMatter(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md": "---\ntitle: Front title\nposition: 4\ncompliance: true\nother: ignored\n---\n# Heading stays\nbody\n",
		"b.md": "\ufeff---\r\ntitle: Bom\r\n---\r\nbody\r\n",
		"c.md": "---\n---\n# From heading\n",
	})
	b := read(t, dir, "r")
	a := find(t, b, "r/a")
	if titleOf(a, "EN") != "Front title" || a.Position == nil || *a.Position != 4 || a.Compliance == nil || !*a.Compliance {
		t.Fatalf("a = %+v", a)
	}
	if body := a.Translations[0].Body(nil, nil); !strings.Contains(body, "# Heading stays") {
		t.Fatalf("a front title keeps the heading in the body, got %q", body)
	}
	if titleOf(find(t, b, "r/b"), "EN") != "Bom" {
		t.Fatal("BOM and CRLF front matter must parse")
	}
	c := find(t, b, "r/c")
	if titleOf(c, "EN") != "From heading" || c.Position != nil || c.Compliance != nil {
		t.Fatalf("c = %+v (unset fields must stay nil)", c)
	}
}

func TestTitleHeadingRules(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md": "intro\n\n# Late title ##\n\ntext\n",
		"b.md": "```\n# not a title\n```\n\n# Real\n",
		"c.md": "## only second level\n",
	})
	b := read(t, dir, "r")
	if got := titleOf(find(t, b, "r/a"), "EN"); got != "Late title" {
		t.Fatalf("a title = %q", got)
	}
	a := find(t, b, "r/a").Translations[0].Body(nil, nil)
	if strings.Contains(a, "Late title") || !strings.Contains(a, "intro") || !strings.Contains(a, "text") {
		t.Fatalf("a body = %q", a)
	}
	if got := titleOf(find(t, b, "r/b"), "EN"); got != "Real" {
		t.Fatalf("b title = %q, a heading in a code block is no title", got)
	}
	if got := titleOf(find(t, b, "r/c"), "EN"); got != "c" {
		t.Fatalf("c title = %q, want the slug", got)
	}
}

func TestLinkRewriteToPages(t *testing.T) {
	dir := tree(t, map[string]string{
		"index.md":     "# Home\n[guide](guide.md) [sub](sub/index.md#part) [rd](sub/index.md?x=1) [up](../outside.md) [abs](/already) [web](https://x.io/a.md) [anc](#top) [mail](mailto:a@b.c) [missing](nope.md)\n\n[ref]: guide.md\n",
		"guide.md":     "# G\n",
		"sub/index.md": "# S\n[back](../index.md)\n",
	})
	b := read(t, dir, "r/docs")
	home := find(t, b, "r/docs").Translations[0].Body(nil, nil)
	for _, want := range []string{
		"[guide](/r/docs/guide)", "[sub](/r/docs/sub#part)", "[rd](/r/docs/sub)", "[up](../outside.md)", "[abs](/already)",
		"[web](https://x.io/a.md)", "[anc](#top)", "[mail](mailto:a@b.c)", "[missing](nope.md)", "[ref]: /r/docs/guide",
	} {
		if !strings.Contains(home, want) {
			t.Errorf("body lacks %q:\n%s", want, home)
		}
	}
	if body := find(t, b, "r/docs/sub").Translations[0].Body(nil, nil); !strings.Contains(body, "[back](/r/docs)") {
		t.Fatalf("sub body = %q", body)
	}
}

func TestLinksInCodeBlocksStayAsWritten(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md": "# A\n```\n[x](b.md) ![i](logo.png)\n```\n[y](b.md)\n",
		"b.md": "# B\n", "logo.png": "png",
	})
	p := find(t, read(t, dir, "r"), "r/a")
	body := p.Translations[0].Body(p.Assets, nil)
	if !strings.Contains(body, "[x](b.md)") || !strings.Contains(body, "[y](/r/b)") {
		t.Fatalf("body = %q", body)
	}
	if len(p.Assets) != 0 {
		t.Fatalf("an image in a code block is no asset: %+v", p.Assets)
	}
}

func TestAssetsNamedByHashAndRewritten(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md":           "# A\n![logo](img/logo%20x.png \"title\") and [again](img/logo%20x.png) <img>\n[doc](files/spec.pdf#page=2)\n",
		"a.tr.md":        "# A tr\n![logo](img/logo%20x.png)\n",
		"img/logo x.png": "PNGDATA",
		"files/spec.pdf": "PDFDATA",
	})
	b := read(t, dir, "r")
	p := find(t, b, "r/a")
	if len(p.Assets) != 2 {
		t.Fatalf("assets = %+v, want 2 (same file once per page across locales)", p.Assets)
	}
	var logo Asset
	for _, a := range p.Assets {
		if strings.HasSuffix(a.Name, "-logo x.png") {
			logo = a
		}
	}
	if len(logo.Name) != 12+1+len("logo x.png") || logo.Size != int64(len("PNGDATA")) || !strings.HasSuffix(logo.Source, filepath.Join("img", "logo x.png")) {
		t.Fatalf("logo = %+v", logo)
	}
	again := read(t, dir, "r")
	if find(t, again, "r/a").Assets[0].Name != p.Assets[0].Name {
		t.Fatal("an unchanged file must keep its name across runs")
	}
	// Without a URL the link stays as written; with one, it is replaced.
	en := p.Translations[1] // locales are sorted: EN before TR
	if en.Locale != "EN" {
		en = p.Translations[0]
	}
	if body := en.Body(p.Assets, nil); !strings.Contains(body, "(img/logo%20x.png \"title\")") || !strings.Contains(body, "(files/spec.pdf#page=2)") {
		t.Fatalf("body without urls = %q", body)
	}
	urls := map[string]string{logo.Name: "https://cdn/logo.png"}
	body := en.Body(p.Assets, urls)
	if !strings.Contains(body, "![logo](https://cdn/logo.png \"title\")") || !strings.Contains(body, "[again](https://cdn/logo.png)") {
		t.Fatalf("body with urls = %q", body)
	}
	if !strings.Contains(body, "(files/spec.pdf#page=2)") {
		t.Fatalf("an asset without a url keeps its link: %q", body)
	}
}

func TestAssetRules(t *testing.T) {
	outside := tree(t, map[string]string{"secret.png": "x"})
	dir := tree(t, map[string]string{
		"empty.md":    "# E\n![x](zero.png)\n",
		"zero.png":    "",
		"ok.md":       "# O\n![x](ok.png)\n",
		"ok.png":      "x",
		"leave.md":    "# L\n![x](../" + filepath.Base(outside) + "/secret.png)\n",
		"dirlink.md":  "# D\n![x](ok.md/)\n[d](subdir)\n",
		"subdir/z.md": "# Z\n",
	})
	b := read(t, dir, "r")
	if p := find(t, b, "r/empty"); p.AssetErr == nil || !strings.Contains(p.AssetErr.Error(), "empty") || len(p.Assets) != 0 {
		t.Fatalf("empty asset: %+v", p)
	}
	if p := find(t, b, "r/ok"); p.AssetErr != nil || len(p.Assets) != 1 {
		t.Fatalf("ok: %+v", p)
	}
	if p := find(t, b, "r/leave"); len(p.Assets) != 0 || p.AssetErr != nil {
		t.Fatalf("a link leaving the directory is no asset: %+v", p)
	}
	if p := find(t, b, "r/dirlink"); len(p.Assets) != 0 || p.AssetErr != nil {
		t.Fatalf("a directory link is no asset: %+v", p)
	}
}

func TestSymlinkedDirectoryLeadingOutsideIsNoAsset(t *testing.T) {
	outside := tree(t, map[string]string{"secret.png": "x"})
	dir := tree(t, map[string]string{"a.md": "# A\n![x](linked/secret.png)\n"})
	if err := os.Symlink(outside, filepath.Join(dir, "linked")); err != nil {
		t.Fatal(err)
	}
	p := find(t, read(t, dir, "r"), "r/a")
	if len(p.Assets) != 0 || p.AssetErr != nil {
		t.Fatalf("a file reached through a link leading outside the directory is no asset: %+v", p)
	}
	if body := p.Translations[0].Body(p.Assets, nil); !strings.Contains(body, "(linked/secret.png)") {
		t.Fatalf("the link must stay as written: %q", body)
	}
}

func TestSymlinkedDirectoryStayingInsideIsAnAsset(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md":         "# A\n![x](inner/logo.png)\n",
		"img/logo.png": "PNGDATA",
	})
	if err := os.Symlink(filepath.Join(dir, "img"), filepath.Join(dir, "inner")); err != nil {
		t.Fatal(err)
	}
	p := find(t, read(t, dir, "r"), "r/a")
	if len(p.Assets) != 1 || p.AssetErr != nil || p.Assets[0].Size != int64(len("PNGDATA")) {
		t.Fatalf("a file reached through a link staying inside the directory is an asset: %+v", p)
	}
}

func TestOversizedAssetFailsItsPageOnly(t *testing.T) {
	dir := tree(t, map[string]string{"a.md": "# A\n![x](big.bin)\n", "b.md": "# B\n"})
	f, err := os.Create(filepath.Join(dir, "big.bin"))
	if err != nil {
		t.Fatal(err)
	}
	if err := f.Truncate(MaxAssetBytes + 1); err != nil {
		t.Fatal(err)
	}
	_ = f.Close()
	b := read(t, dir, "r")
	if p := find(t, b, "r/a"); p.AssetErr == nil || !strings.Contains(p.AssetErr.Error(), "12 MB") {
		t.Fatalf("a = %+v", p)
	}
	if p := find(t, b, "r/b"); p.AssetErr != nil {
		t.Fatalf("b must be unaffected: %+v", p)
	}
}
