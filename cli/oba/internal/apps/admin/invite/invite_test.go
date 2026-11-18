package invite

import (
	"testing"
	"time"
)

func TestLocalDay(t *testing.T) {
	istanbul := time.FixedZone("TRT", 3*60*60)
	newYork := time.FixedZone("EST", -5*60*60)
	instant := "2026-11-22T22:30:00Z"
	garbled := "soon\nX"
	tests := []struct {
		name  string
		value *string
		loc   *time.Location
		want  string
	}{
		{"UTC", &instant, time.UTC, "2026-11-22"},
		{"east of UTC moves to the next day", &instant, istanbul, "2026-11-23"},
		{"west of UTC stays", &instant, newYork, "2026-11-22"},
		{"none", nil, time.UTC, ""},
		{"unreadable shown on one line", &garbled, time.UTC, "soon X"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := localDay(tt.value, tt.loc); got != tt.want {
				t.Errorf("localDay = %q, want %q", got, tt.want)
			}
		})
	}
}
