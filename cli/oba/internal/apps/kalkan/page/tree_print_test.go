package page

import (
	"bytes"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestPrintTreeOrdersByPositionThenSlug(t *testing.T) {
	pages := []client.KalkanPageListItem{
		{ID: "r1", Slug: "engineering", Path: "engineering", Position: 1, Titles: map[string]string{"EN": "Engineering"}, Level: "EDIT"},
		{ID: "r2", Slug: "isms", Path: "isms", Position: 0, Compliance: true, Titles: map[string]string{"TR": "BGYS"}, Level: "VIEW"},
		{ID: "p2", ParentID: "r1", Slug: "runbooks", Path: "engineering/runbooks"},
		{ID: "p1", ParentID: "r1", Slug: "plans", Path: "engineering/plans", Titles: map[string]string{"EN": "Plans", "TR": "Planlar"}},
		{ID: "p3", ParentID: "p1", Slug: "q4", Path: "engineering/plans/q4", Compliance: true, Titles: map[string]string{"TR": "Ç4"}},
	}
	var out bytes.Buffer
	PrintTree(&out, pages)
	want := "isms  BGYS  [compliance]  (VIEW)\n" +
		"engineering  Engineering  (EDIT)\n" +
		"  engineering/plans  Plans\n" +
		"    engineering/plans/q4  Ç4  [compliance]\n" +
		"  engineering/runbooks\n"
	if out.String() != want {
		t.Errorf("out =\n%s\nwant\n%s", out.String(), want)
	}
}

// The top of a subtree has a parent that is not printed, and starts the first level.
func TestPrintTreeOfSubtree(t *testing.T) {
	pages := []client.KalkanPageListItem{
		{ID: "p1", ParentID: "r1", Slug: "plans", Path: "engineering/plans"},
		{ID: "p3", ParentID: "p1", Slug: "q4", Path: "engineering/plans/q4", Titles: map[string]string{"EN": "Q4\nnotes"}},
	}
	var out bytes.Buffer
	PrintTree(&out, pages)
	if want := "engineering/plans\n  engineering/plans/q4  Q4 notes\n"; out.String() != want {
		t.Errorf("out = %q, want %q", out.String(), want)
	}
}

func TestPrintTreeEmpty(t *testing.T) {
	var out bytes.Buffer
	PrintTree(&out, nil)
	if out.String() != "No pages.\n" {
		t.Errorf("out = %q", out.String())
	}
}
