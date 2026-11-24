package role

import "testing"

func TestParseRole(t *testing.T) {
	tests := []struct {
		given, want, wantErr string
	}{
		{"ADMIN", "ADMIN", ""},
		{"member", "MEMBER", ""},
		{" Guest ", "GUEST", ""},
		{"OWNER", "", `unknown role "OWNER"; allowed: ADMIN, MEMBER, GUEST`},
		{"owner", "", `unknown role "owner"; allowed: ADMIN, MEMBER, GUEST`},
		{"boss", "", `unknown role "boss"; allowed: ADMIN, MEMBER, GUEST`},
		{"", "", `unknown role ""; allowed: ADMIN, MEMBER, GUEST`},
	}
	for _, tt := range tests {
		t.Run(tt.given, func(t *testing.T) {
			got, err := ParseRole(tt.given)
			if tt.wantErr != "" {
				if err == nil || err.Error() != tt.wantErr {
					t.Fatalf("err = %v, want %q", err, tt.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if got != tt.want {
				t.Errorf("got %q, want %q", got, tt.want)
			}
		})
	}
}
