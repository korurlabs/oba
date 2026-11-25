package upload

import (
	"strings"
	"testing"
)

func TestTitleHeadingRules(t *testing.T) {
	dir := tree(t, map[string]string{
		"a.md": "intro\n\n# Late title ##\n\ntext\n",
		"b.md": "```\n# not a title\n```\n\n# Real\n",
		"c.md": "## only second level\n",
	})
	b := mustRead(t, dir, "r")
	if got := titleOf(findPage(t, b, "r/a"), "EN"); got != "Late title" {
		t.Fatalf("a title = %q", got)
	}
	a := findPage(t, b, "r/a").Translations[0].Body(nil, nil)
	if strings.Contains(a, "Late title") || !strings.Contains(a, "intro") || !strings.Contains(a, "text") {
		t.Fatalf("a body = %q", a)
	}
	if got := titleOf(findPage(t, b, "r/b"), "EN"); got != "Real" {
		t.Fatalf("b title = %q, a heading in a code block is no title", got)
	}
	if got := titleOf(findPage(t, b, "r/c"), "EN"); got != "c" {
		t.Fatalf("c title = %q, want the slug", got)
	}
}
