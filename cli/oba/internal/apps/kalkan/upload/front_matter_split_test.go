package upload

import (
	"strings"
	"testing"
)

func TestFrontMatter(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md": "---\ntitle: Front title\nposition: 4\ncompliance: true\nother: ignored\n---\n# Heading stays\nbody\n",
		"b.md": "\ufeff---\r\ntitle: Bom\r\n---\r\nbody\r\n",
		"c.md": "---\n---\n# From heading\n",
	})
	b := mustRead(t, dir, "r")
	a := findPage(t, b, "r/a")
	if titleOf(a, "EN") != "Front title" || a.Position == nil || *a.Position != 4 || a.Compliance == nil || !*a.Compliance {
		t.Fatalf("a = %+v", a)
	}
	if body := a.Translations[0].Body(nil, nil); !strings.Contains(body, "# Heading stays") {
		t.Fatalf("a front title keeps the heading in the body, got %q", body)
	}
	if titleOf(findPage(t, b, "r/b"), "EN") != "Bom" {
		t.Fatal("BOM and CRLF front matter must parse")
	}
	c := findPage(t, b, "r/c")
	if titleOf(c, "EN") != "From heading" || c.Position != nil || c.Compliance != nil {
		t.Fatalf("c = %+v (unset fields must stay nil)", c)
	}
}
