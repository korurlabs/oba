package pr

import (
	"strings"
	"testing"
)

func TestCheckNoteCountsUTF16Units(t *testing.T) {
	fits := strings.Repeat("😀", MaxNoteLength/2)
	if err := CheckNote(&fits); err != nil {
		t.Errorf("250 emoji refused: %v", err)
	}
	over := fits + "a"
	if err := CheckNote(&over); err == nil || !strings.Contains(err.Error(), "got 501") {
		t.Errorf("err = %v", err)
	}
	if err := CheckNote(nil); err != nil {
		t.Error(err)
	}
}
