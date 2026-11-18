package dates

import (
	"fmt"
	"strings"
	"time"
)

// ParseDate reads a YYYY-MM-DD day as 00:00 in the zone of the machine the CLI runs on. A
// calendar day names a day, not an instant, so it is read as the viewer's day and compares
// directly with today's day.
func ParseDate(value string) (time.Time, error) {
	t, err := time.ParseInLocation(time.DateOnly, value, time.Local)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid date %q: use YYYY-MM-DD", value)
	}
	return t, nil
}

// ToAPIDate checks a calendar day the user typed and returns it trimmed. The API reads such a
// field as a day, not an instant, so the value is sent as typed, without a zone.
func ToAPIDate(value string) (string, error) {
	v := strings.TrimSpace(value)
	if _, err := ParseDate(v); err != nil {
		return "", fmt.Errorf("invalid date %q: use YYYY-MM-DD", value)
	}
	return v, nil
}
