package pr

import "testing"

func TestParseStatus(t *testing.T) {
	for raw, want := range map[string]string{"in-review": "IN_REVIEW", "In Review": "IN_REVIEW", " merged ": "MERGED", "discuss": "DISCUSS"} {
		if got, err := ParseStatus(raw); err != nil || got != want {
			t.Errorf("%q: got %q, %v", raw, got, err)
		}
	}
	if _, err := ParseStatus("closed"); err == nil {
		t.Error("closed was accepted")
	}
}

func TestPresetStatus(t *testing.T) {
	for state, want := range map[string]string{"DRAFT": "DRAFT", "OPEN": "OPEN", "MERGED": "MERGED"} {
		if got, ok := PresetStatus(state); !ok || got != want {
			t.Errorf("%s: got %q, %v", state, got, ok)
		}
	}
	if _, ok := PresetStatus("CLOSED"); ok {
		t.Error("CLOSED has a preset")
	}
}
