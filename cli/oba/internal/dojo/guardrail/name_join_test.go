package guardrail

import "testing"

func TestPersonName(t *testing.T) {
	cases := []struct{ first, last, fallback, want string }{
		{"Ada", "Lovelace", "ada@example.com", "Ada Lovelace"},
		{" Ada ", "", "ada@example.com", "Ada"},
		{"", "", "ada@example.com", "ada@example.com"},
		{" ", "\t", "-", "-"},
		{"", "", "", ""},
		{"Ada\x1b[31m", "Love\nlace", "x", "Ada Love lace"},
	}
	for _, tc := range cases {
		if got := PersonName(tc.first, tc.last, tc.fallback); got != tc.want {
			t.Errorf("PersonName(%q, %q, %q) = %q, want %q", tc.first, tc.last, tc.fallback, got, tc.want)
		}
	}
}
