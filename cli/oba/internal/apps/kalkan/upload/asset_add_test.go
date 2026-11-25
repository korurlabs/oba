package upload

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestAssetsNamedByHashAndRewritten(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md":           "# A\n![logo](img/logo%20x.png \"title\") and [again](img/logo%20x.png) <img>\n[doc](files/spec.pdf#page=2)\n",
		"a.tr.md":        "# A tr\n![logo](img/logo%20x.png)\n",
		"img/logo x.png": "PNGDATA",
		"files/spec.pdf": "PDFDATA",
	})
	b := mustRead(t, dir, "r")
	p := findPage(t, b, "r/a")
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
	again := mustRead(t, dir, "r")
	if findPage(t, again, "r/a").Assets[0].Name != p.Assets[0].Name {
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
	b := mustRead(t, dir, "r")
	if p := findPage(t, b, "r/empty"); p.AssetErr == nil || !strings.Contains(p.AssetErr.Error(), "empty") || len(p.Assets) != 0 {
		t.Fatalf("empty asset: %+v", p)
	}
	if p := findPage(t, b, "r/ok"); p.AssetErr != nil || len(p.Assets) != 1 {
		t.Fatalf("ok: %+v", p)
	}
	if p := findPage(t, b, "r/leave"); len(p.Assets) != 0 || p.AssetErr != nil {
		t.Fatalf("a link leaving the directory is no asset: %+v", p)
	}
	if p := findPage(t, b, "r/dirlink"); len(p.Assets) != 0 || p.AssetErr != nil {
		t.Fatalf("a directory link is no asset: %+v", p)
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
	b := mustRead(t, dir, "r")
	if p := findPage(t, b, "r/a"); p.AssetErr == nil || !strings.Contains(p.AssetErr.Error(), "12 MB") {
		t.Fatalf("a = %+v", p)
	}
	if p := findPage(t, b, "r/b"); p.AssetErr != nil {
		t.Fatalf("b must be unaffected: %+v", p)
	}
}
