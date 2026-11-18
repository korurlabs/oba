package pr

import (
	"reflect"
	"testing"
)

func TestDescribeChanges(t *testing.T) {
	note := "keep me"
	before := Edit{
		Status: "IN_REVIEW", Note: &note,
		ReviewerIDs: []string{"a", "b"}, Names: map[string]string{"a": "Ada", "b": "Bob"},
		TicketIDs: []string{"t1"}, TicketLabels: map[string]string{"t1": "ACME-1"},
	}
	after := Edit{
		Status: "MERGED", Note: nil,
		ReviewerIDs: []string{"b", "c"}, Names: map[string]string{"a": "Ada", "b": "Bob", "c": "cy@x.io"},
		TicketIDs: []string{"t1"}, TicketLabels: map[string]string{"t1": "ACME-1"},
	}
	want := []string{
		"Status: In Review -> Merged",
		`Note: "keep me" -> none`,
		"Reviewers added: cy@x.io",
		"Reviewers removed: Ada (their approval is dropped)",
	}
	if got := DescribeChanges(before, after); !reflect.DeepEqual(got, want) {
		t.Errorf("got %q", got)
	}

	reordered := before
	reordered.ReviewerIDs = []string{"b", "a"}
	if got := DescribeChanges(before, reordered); !reflect.DeepEqual(got, []string{"Reviewers reordered"}) {
		t.Errorf("reordered: %q", got)
	}
	if got := DescribeChanges(before, before); len(got) != 0 {
		t.Errorf("no change: %q", got)
	}
}
