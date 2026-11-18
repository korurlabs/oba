package updates

import "testing"

func TestParseID(t *testing.T) {
	if id, err := ParseID(" " + testTicketID + " "); err != nil || id != testTicketID {
		t.Errorf("id %q err %v", id, err)
	}
	for _, bad := range []string{"OPS-7", "", "1234"} {
		if _, err := ParseID(bad); err == nil {
			t.Errorf("%q: want an error", bad)
		}
	}
}
