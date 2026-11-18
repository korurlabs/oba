package board

import (
	"strings"
	"testing"
)

func TestParseMemberSpecs(t *testing.T) {
	got, err := ParseMemberSpecs([]string{"a@b.c:admin", " u:1:viewer "})
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got[0] != (MemberSpec{User: "a@b.c", Role: "ADMIN"}) || got[1] != (MemberSpec{User: "u:1", Role: "VIEWER"}) {
		t.Errorf("got %+v", got)
	}
	for _, bad := range []string{"a@b.c", ":ADMIN", "a@b.c:", "a@b.c:BOSS"} {
		if _, err := ParseMemberSpecs([]string{bad}); err == nil || !strings.Contains(err.Error(), "invalid --member") {
			t.Errorf("%q: err = %v", bad, err)
		}
	}
}
