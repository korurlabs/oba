package pr

import "testing"

func TestParseLogins(t *testing.T) {
	got, err := ParseLogins("ghopper, dependabot[bot]")
	if err != nil || len(got) != 2 || got[1] != "dependabot[bot]" {
		t.Fatalf("got %v, %v", got, err)
	}
	for _, bad := range []string{"a@b.c", "-lead", "a,b,c,d,e,f"} {
		if _, err := ParseLogins(bad); err == nil {
			t.Errorf("%q accepted", bad)
		}
	}
}
