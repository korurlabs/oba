package icp

import (
	"testing"
)

func TestSizeRange(t *testing.T) {
	s, m := "S", "M"
	for _, tt := range []struct {
		lo, hi *string
		want   string
	}{{nil, nil, "any"}, {&s, nil, "at least S"}, {nil, &m, "at most M"}, {&s, &m, "S to M"}} {
		if got := SizeRange(tt.lo, tt.hi); got != tt.want {
			t.Errorf("sizeRange = %q, want %q", got, tt.want)
		}
	}
}
