package updates

import (
	"strings"
	"testing"
)

func TestParseLineFlagsReadsKeyValuePairs(t *testing.T) {
	f, err := ParseLineFlags([]string{"OPS-7=40", " kagi-2 = 5 "}, []string{"OPS-7= Waiting, still ", "KAGI-2="})
	if err != nil {
		t.Fatal(err)
	}
	if len(f.percents) != 2 || f.percents[0] != (lineFlagValue{ref: "OPS-7", percentage: 40}) || f.percents[1].ref != "kagi-2" || f.percents[1].percentage != 5 {
		t.Errorf("percents = %+v", f.percents)
	}
	if len(f.notes) != 2 || f.notes[0].note != "Waiting, still" || f.notes[1].note != "" {
		t.Errorf("notes = %+v", f.notes)
	}
	if f.Empty() {
		t.Error("Empty() = true")
	}
}

func TestParseLineFlagsErrors(t *testing.T) {
	cases := []struct {
		name            string
		percents, notes []string
		want            string
	}{
		{"no equals", []string{"OPS-7"}, nil, "KEY=VALUE"},
		{"no key", []string{"=4"}, nil, "KEY=VALUE"},
		{"not a number", []string{"OPS-7=x"}, nil, "whole number from 0 to 100"},
		{"above range", []string{"OPS-7=101"}, nil, "whole number from 0 to 100"},
		{"twice, case ignored", []string{"OPS-7=1", "ops-7=2"}, nil, "--percent names ops-7 twice"},
		{"note twice", nil, []string{"OPS-7=a", "OPS-7=b"}, "--note names OPS-7 twice"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := ParseLineFlags(tc.percents, tc.notes)
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("err = %v, want %q", err, tc.want)
			}
		})
	}
}

func TestParsePercentageBounds(t *testing.T) {
	for _, v := range []string{"0", "100", " 42 "} {
		if _, err := ParsePercentage(v); err != nil {
			t.Errorf("%q: %v", v, err)
		}
	}
	for _, v := range []string{"-1", "101", "4.5", ""} {
		if _, err := ParsePercentage(v); err == nil {
			t.Errorf("%q: want an error", v)
		}
	}
}
