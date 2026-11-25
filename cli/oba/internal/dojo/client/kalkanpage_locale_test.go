package client

import "testing"

func TestKalkanLocale(t *testing.T) {
	for in, want := range map[string]string{"tr": KalkanLocaleTR, "EN": KalkanLocaleEN, " En ": KalkanLocaleEN} {
		if got, err := KalkanLocale(in); err != nil || got != want {
			t.Errorf("KalkanLocale(%q) = %q, %v; want %q", in, got, err, want)
		}
	}
	for _, in := range []string{"", "de", "turkish"} {
		if _, err := KalkanLocale(in); err == nil {
			t.Errorf("KalkanLocale(%q) gave no error", in)
		}
	}
}
