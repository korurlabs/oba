package cyberscore

import (
	"strings"
	"testing"
)

func TestParseFoldsCaseDashAndSpace(t *testing.T) {
	for _, tc := range []struct {
		parse func(string) (string, error)
		raw   string
		want  string
	}{
		{ParseStatus, "in-progress", "IN_PROGRESS"},
		{ParseStatus, " Not Started ", "NOT_STARTED"},
		{ParseOutcome, "no-license", "NO_LICENSE"},
		{ParseOutcome, "pass", "PASS"},
		{ParseCategory, "development supply-chain", "DEVELOPMENT_SUPPLY_CHAIN"},
		{ParseCategory, "Identity", "IDENTITY"},
	} {
		got, err := tc.parse(tc.raw)
		if err != nil || got != tc.want {
			t.Errorf("%q: got %q, %v; want %q", tc.raw, got, err, tc.want)
		}
	}
}

func TestParseInvalidListsTheValues(t *testing.T) {
	_, err := ParseOutcome("passed")
	if err == nil || err.Error() != `invalid outcome "passed": use one of TODO, PASS, PARTIAL, FAIL, NO_LICENSE, NOT_APPLICABLE` {
		t.Errorf("err = %v", err)
	}
	_, err = ParseCategory("NETWORK_OFFICE")
	if err == nil || !strings.Contains(err.Error(), "IDENTITY, OFFICE, NETWORK_EDGE") {
		t.Errorf("err = %v", err)
	}
	if _, err := ParseStatus(""); err == nil {
		t.Error("empty status: want an error")
	}
}
