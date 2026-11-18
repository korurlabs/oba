package pr

import (
	"strings"
	"testing"
)

func TestParseReviewers(t *testing.T) {
	if _, err := ParseReviewers("reviewers", "a@x.io, A@X.io"); err == nil || !strings.Contains(err.Error(), "--reviewers names A@X.io twice") {
		t.Errorf("twice: err = %v", err)
	}
	if _, err := ParseReviewers("reviewers", strings.Repeat("u@x.io,", 11)); err == nil {
		t.Error("eleven reviewers accepted")
	}
	if got, err := ParseReviewers("reviewers", ""); err != nil || len(got) != 0 {
		t.Errorf("empty: %v, %v", got, err)
	}
}
