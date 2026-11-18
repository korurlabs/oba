package roadmap

import (
	"strings"
	"testing"
)

func TestOptionsCheck(t *testing.T) {
	cases := []struct {
		from, to string
		wantErr  string
	}{
		{from: "", to: ""},
		{from: "2026-10-01", to: ""},
		{from: "", to: "2026-12-31"},
		{from: "2026-10-01", to: "2026-10-01"},
		{from: "2026-10-01", to: "2026-12-31"},
		{from: " 2026-10-01 ", to: "\t2026-12-31\n"},
		{from: "2026-12-31", to: "2026-10-01", wantErr: "is after --to"},
		{from: "October", to: "", wantErr: "--from must be a date"},
		{from: "", to: "2026-12-32", wantErr: "--to must be a date"},
	}
	for _, tc := range cases {
		err := Options{From: tc.from, To: tc.to}.Check()
		if tc.wantErr == "" {
			if err != nil {
				t.Errorf("Options{%q, %q}.Check() returned %v", tc.from, tc.to, err)
			}
			continue
		}
		if err == nil || !strings.Contains(err.Error(), tc.wantErr) {
			t.Errorf("Options{%q, %q}.Check() = %v, want an error containing %q", tc.from, tc.to, err, tc.wantErr)
		}
	}
}
