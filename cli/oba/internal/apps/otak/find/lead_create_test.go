package find

import (
	"testing"
)

func TestCreatedCounts(t *testing.T) {
	tests := []struct {
		leads, leadsTotal, contacts, contactsTotal int
		want                                       string
	}{
		{2, 2, 0, 0, "2 leads"},
		{2, 2, 3, 3, "2 leads and 3 contacts"},
		{1, 2, 3, 3, "1 of 2 leads and 3 contacts"},
		{2, 2, 1, 3, "2 leads and 1 of 3 contacts"},
	}
	for _, tt := range tests {
		if got := createdCounts(tt.leads, tt.leadsTotal, tt.contacts, tt.contactsTotal); got != tt.want {
			t.Errorf("createdCounts(%+v) = %q, want %q", tt, got, tt.want)
		}
	}
}
