package organization

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestFindMember(t *testing.T) {
	members := []client.Member{
		{UserID: "u-1", Email: "Ana@Example.com"},
		{UserID: "bob@example.com", Email: "other@example.com"},
		{UserID: "u-3", Email: "bob@example.com"},
	}
	tests := []struct {
		name, value, wantUserID string
	}{
		{"user id", "u-1", "u-1"},
		{"e-mail, case ignored", "ana@example.COM", "u-1"},
		{"surrounding spaces", "  u-3 ", "u-3"},
		{"user id wins over e-mail", "bob@example.com", "bob@example.com"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := FindMember(members, "Acme", tt.value)
			if err != nil {
				t.Fatal(err)
			}
			if got.UserID != tt.wantUserID {
				t.Errorf("UserID = %q, want %q", got.UserID, tt.wantUserID)
			}
		})
	}
}

func TestFindMemberNoMatch(t *testing.T) {
	members := []client.Member{{UserID: "u-1", Email: "ana@example.com"}}
	for _, value := range []string{"U-1", "nobody@example.com"} {
		_, err := FindMember(members, "Acme", value)
		want := `no member of Acme matches "` + value + `"; run 'oba ulak member list'`
		if err == nil || err.Error() != want || client.ExitCode(err) != 2 {
			t.Errorf("FindMember(%q) err = %v, exit = %d", value, err, client.ExitCode(err))
		}
	}
}

func TestFindMemberNoMatchSanitizesMessage(t *testing.T) {
	_, err := FindMember(nil, "Ac\nme", "x\x1b[2Jy")
	if err == nil || err.Error() != `no member of Ac me matches "xy"; run 'oba ulak member list'` {
		t.Errorf("err = %v", err)
	}
}
