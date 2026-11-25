package upload

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestLinkRewriteToPages(t *testing.T) {
	dir := tree(t, map[string]string{
		"index.md":     "# Home\n[guide](guide.md) [sub](sub/index.md#part) [rd](sub/index.md?x=1) [up](../outside.md) [abs](/already) [web](https://x.io/a.md) [anc](#top) [mail](mailto:a@b.c) [missing](nope.md)\n\n[ref]: guide.md\n",
		"guide.md":     "# G\n",
		"sub/index.md": "# S\n[back](../index.md)\n",
	})
	b := mustRead(t, dir, "r/docs")
	home := findPage(t, b, "r/docs").Translations[0].Body(nil, nil)
	for _, want := range []string{
		"[guide](/r/docs/guide)", "[sub](/r/docs/sub#part)", "[rd](/r/docs/sub)", "[up](../outside.md)", "[abs](/already)",
		"[web](https://x.io/a.md)", "[anc](#top)", "[mail](mailto:a@b.c)", "[missing](nope.md)", "[ref]: /r/docs/guide",
	} {
		if !strings.Contains(home, want) {
			t.Errorf("body lacks %q:\n%s", want, home)
		}
	}
	if body := findPage(t, b, "r/docs/sub").Translations[0].Body(nil, nil); !strings.Contains(body, "[back](/r/docs)") {
		t.Fatalf("sub body = %q", body)
	}
}

func TestLinksInCodeBlocksStayAsWritten(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md": "# A\n```\n[x](b.md) ![i](logo.png)\n```\n[y](b.md)\n",
		"b.md": "# B\n", "logo.png": "png",
	})
	p := findPage(t, mustRead(t, dir, "r"), "r/a")
	body := p.Translations[0].Body(p.Assets, nil)
	if !strings.Contains(body, "[x](b.md)") || !strings.Contains(body, "[y](/r/b)") {
		t.Fatalf("body = %q", body)
	}
	if len(p.Assets) != 0 {
		t.Fatalf("an image in a code block is no asset: %+v", p.Assets)
	}
}

func TestSymlinkedDirectoryLeadingOutsideIsNoAsset(t *testing.T) {
	outside := tree(t, map[string]string{"secret.png": "x"})
	dir := tree(t, map[string]string{"a.md": "# A\n![x](linked/secret.png)\n"})
	if err := os.Symlink(outside, filepath.Join(dir, "linked")); err != nil {
		t.Fatal(err)
	}
	p := findPage(t, mustRead(t, dir, "r"), "r/a")
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
	p := findPage(t, mustRead(t, dir, "r"), "r/a")
	if len(p.Assets) != 1 || p.AssetErr != nil || p.Assets[0].Size != int64(len("PNGDATA")) {
		t.Fatalf("a file reached through a link staying inside the directory is an asset: %+v", p)
	}
}
