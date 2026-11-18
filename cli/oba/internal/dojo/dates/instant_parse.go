package dates

import (
	"fmt"
	"strings"
	"time"
)

// ToAPIInstant turns a date the user typed into the RFC 3339 UTC instant an instant field of the
// API takes; the API refuses a bare YYYY-MM-DD there. A calendar date means local midnight, the
// instant the web app sends for a picked day. A full RFC 3339 value keeps its instant in UTC.
func ToAPIInstant(value string) (string, error) {
	v := strings.TrimSpace(value)
	if d, err := time.ParseInLocation(time.DateOnly, v, time.Local); err == nil {
		return d.UTC().Format(time.RFC3339), nil
	}
	if t, err := time.Parse(time.RFC3339, v); err == nil {
		return t.UTC().Format(time.RFC3339Nano), nil
	}
	return "", fmt.Errorf("invalid date %q: use YYYY-MM-DD or an RFC 3339 timestamp such as 2026-10-14T09:00:00Z", value)
}
