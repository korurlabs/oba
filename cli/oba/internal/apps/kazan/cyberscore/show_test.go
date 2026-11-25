package cyberscore

import (
	"bytes"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestPrintScoreBreakdownInCategoryOrderUnknownLast(t *testing.T) {
	var out bytes.Buffer
	at := "2026-11-25T10:00:00Z"
	err := PrintScore(&out, client.Cyberscore{
		ScorePercentage: 72, ScoreLevel: "LOW", ScorableCheckCount: 5, LastCalculatedAt: &at,
		BreakdownByCategory: map[string]int{"COMPLIANCE": 40, "ZETA": 1, "IDENTITY": 80, "ALPHA": 2, "DATA": 50},
	})
	if err != nil {
		t.Fatal(err)
	}
	got := out.String()
	if !strings.HasPrefix(got, "Score:       72% (LOW)\nChecks:      5 scorable\nCalculated:  ") {
		t.Errorf("out =\n%s", got)
	}
	want := "Breakdown:\n  IDENTITY    80%\n  DATA        50%\n  COMPLIANCE  40%\n  ALPHA       2%\n  ZETA        1%\n"
	if !strings.HasSuffix(got, want) {
		t.Errorf("out =\n%s\nwant suffix\n%s", got, want)
	}
}

func TestPrintScoreUnknown(t *testing.T) {
	var out bytes.Buffer
	if err := PrintScore(&out, client.Cyberscore{ScoreLevel: "UNKNOWN", BreakdownByCategory: map[string]int{}}); err != nil {
		t.Fatal(err)
	}
	if out.String() != "Not scored yet: no check has a scorable outcome.\n" {
		t.Errorf("out = %q", out.String())
	}
}
