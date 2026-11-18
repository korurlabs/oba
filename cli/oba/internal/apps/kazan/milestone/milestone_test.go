package milestone

import (
	"strings"
	"testing"
)

func TestCheckColor(t *testing.T) {
	valid := []string{"#22C55E", "#abcdef", "#000000"}
	for _, v := range valid {
		if err := CheckColor(v); err != nil {
			t.Errorf("CheckColor(%q) = %v, want nil", v, err)
		}
	}
	invalid := []string{"", "22C55E", "#22C55", "#22C55E0", "#GGGGGG", "red", " #22C55E"}
	for _, v := range invalid {
		err := CheckColor(v)
		if err == nil {
			t.Errorf("CheckColor(%q) = nil, want an error", v)
			continue
		}
		if !strings.Contains(err.Error(), "#RRGGBB") {
			t.Errorf("CheckColor(%q) error %q does not name the format", v, err)
		}
	}
}
