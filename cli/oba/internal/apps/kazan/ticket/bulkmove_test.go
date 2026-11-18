package ticket

import (
	"strings"
	"testing"
)

func TestCommonBoard(t *testing.T) {
	id, err := commonBoard([]string{"OPS-1", "OPS-2"}, []string{"B1", "b1"})
	if err != nil || id != "B1" {
		t.Errorf("id %q err %v", id, err)
	}
	_, err = commonBoard([]string{"OPS-1", "KAGI-2", "OPS-3"}, []string{"b1", "b2", "b1"})
	if err == nil || !strings.Contains(err.Error(), "2 different boards (OPS-1,OPS-3 | KAGI-2)") {
		t.Errorf("err = %v", err)
	}
}
