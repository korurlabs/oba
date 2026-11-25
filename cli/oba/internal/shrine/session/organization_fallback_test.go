package session

import "testing"

func TestUseOrganizationUnlessSet(t *testing.T) {
	t.Cleanup(func() { UseOrganization("") })
	tests := []struct {
		name, flag, fallback, want string
	}{
		{"no --org takes the fallback", "", " Acme ", "Acme"},
		{"--org wins", "org-1", "Acme", "org-1"},
		{"empty fallback keeps --org", "org-1", "  ", "org-1"},
		{"neither leaves it empty", "", "", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			UseOrganization(tt.flag)
			UseOrganizationUnlessSet(tt.fallback)
			if got := organizationOverride(); got != tt.want {
				t.Errorf("override = %q, want %q", got, tt.want)
			}
		})
	}
}
