package dates

import (
	"testing"
	"time"
)

func TestToAPIDate(t *testing.T) {
	got, err := ToAPIDate(" 2026-12-01 ")
	if err != nil || got != "2026-12-01" {
		t.Fatalf("ToAPIDate = %q, %v", got, err)
	}
	if _, err := ToAPIDate("next friday"); err == nil {
		t.Fatal("ToAPIDate accepted a value that is not YYYY-MM-DD")
	}
}

func TestParseDateIsLocalMidnight(t *testing.T) {
	d, err := ParseDate("2026-11-04")
	if err != nil {
		t.Fatal(err)
	}
	if d.Location() != time.Local || d.Hour() != 0 || d.Day() != 4 {
		t.Fatalf("ParseDate = %v", d)
	}
}

func TestToAPIInstant(t *testing.T) {
	got, err := ToAPIInstant("2026-10-14T12:00:00+03:00")
	if err != nil || got != "2026-10-14T09:00:00Z" {
		t.Fatalf("ToAPIInstant = %q, %v", got, err)
	}
	day, err := ToAPIInstant("2026-10-14")
	want := time.Date(2026, 10, 14, 0, 0, 0, 0, time.Local).UTC().Format(time.RFC3339)
	if err != nil || day != want {
		t.Fatalf("ToAPIInstant(day) = %q, %v; want %q", day, err, want)
	}
	if _, err := ToAPIInstant("tomorrow"); err == nil {
		t.Fatal("ToAPIInstant accepted an invalid date")
	}
}

func TestFormatInstantKeepsUnparsedValueOnOneLine(t *testing.T) {
	if got := FormatInstant("not\na date"); got == "not\na date" {
		t.Fatalf("FormatInstant = %q", got)
	}
}
