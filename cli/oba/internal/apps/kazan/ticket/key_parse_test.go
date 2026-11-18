package ticket

import "testing"

func TestParseKey(t *testing.T) {
	cases := []struct{ in, prefix, number string }{
		{"OPS-42", "OPS", "42"},
		{"ops-7", "ops", "7"},
		{"MY-TEAM-3", "MY-TEAM", "3"},
	}
	for _, c := range cases {
		prefix, number, err := ParseKey(c.in)
		if err != nil || prefix != c.prefix || number != c.number {
			t.Errorf("ParseKey(%q) = %q %q %v", c.in, prefix, number, err)
		}
	}
	for _, bad := range []string{"OPS", "OPS-", "-1", "OPS-x"} {
		if _, _, err := ParseKey(bad); err == nil {
			t.Errorf("ParseKey(%q): no error", bad)
		}
	}
}
