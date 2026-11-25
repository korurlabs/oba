package cyberscore

import (
	"bytes"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestCheckDaysBounds(t *testing.T) {
	for days, ok := range map[int]bool{0: false, 1: true, 365: true, 366: false, -5: false} {
		if err := CheckDays(days); (err == nil) != ok {
			t.Errorf("%d: err = %v", days, err)
		}
	}
}

func TestPrintTrend(t *testing.T) {
	var out bytes.Buffer
	if err := PrintTrend(&out, nil); err != nil || out.String() != "No trend yet.\n" {
		t.Errorf("out %q err %v", out.String(), err)
	}
	out.Reset()
	err := PrintTrend(&out, []client.CyberscoreTrendPoint{
		{Date: "2026-11-24", ScorePercentage: 60, ScoreLevel: "MEDIUM"},
		{Date: "2026-11-25", ScorePercentage: 72, ScoreLevel: "LOW"},
	})
	want := "DATE        SCORE  LEVEL\n2026-11-24  60%    MEDIUM\n2026-11-25  72%    LOW\n"
	if err != nil || out.String() != want {
		t.Errorf("out =\n%s", out.String())
	}
}
