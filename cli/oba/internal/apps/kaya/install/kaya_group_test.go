package install

import (
	"encoding/json"
	"errors"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

func hookGroups(n int) string {
	groups := make([]string, n)
	for i := range groups {
		groups[i] = `{"hooks":[{"type":"command","command":"guard.sh"}]}`
	}
	return `{"PreToolUse":[` + strings.Join(groups, ",") + `]}`
}

func TestWithoutKayaGroup_boundsTheHookGroupsOfAnEvent(t *testing.T) {
	tests := []struct {
		name     string
		groups   int
		wantKept int
		wantErr  error
	}{
		{name: "no groups", groups: 0, wantKept: 0},
		{name: "groups at the limit", groups: maxHookGroups, wantKept: maxHookGroups},
		{name: "groups above the limit", groups: maxHookGroups + 1, wantErr: ErrTooManyHookGroups},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			hooks, err := jsonedit.Parse([]byte(hookGroups(tt.groups)))
			if err != nil {
				t.Fatal(err)
			}
			kept, err := withoutKayaGroup(hooks, "PreToolUse", "pre-tool-use")
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if len(kept) != tt.wantKept {
				t.Fatalf("kept %d groups, want %d", len(kept), tt.wantKept)
			}
		})
	}
}

// settingsWithHook is a Claude Code settings file whose Stop event runs command.
func settingsWithHook(t *testing.T, command string) []byte {
	t.Helper()
	data, err := json.Marshal(map[string]any{"hooks": map[string]any{"Stop": []any{
		map[string]any{"hooks": []any{map[string]any{"type": "command", "command": command}}},
	}}})
	if err != nil {
		t.Fatal(err)
	}
	return data
}

func TestRegistersRecordHook_findsEverySpellingTheShellRunsAsTheRecordHook(t *testing.T) {
	for command, want := range map[string]bool{
		"oba kaya hook record":                                            true,
		"/opt/homebrew/bin/oba kaya hook record":                          true,
		`'/Users/a b/bin/oba' kaya hook record`:                           true,
		`'/Users/it'\''s/bin/oba' kaya hook record`:                       true,
		"oba  kaya   hook    record":                                      true,
		"oba\tkaya\nhook record":                                          true,
		`"oba" "kaya" "hook" "record"`:                                    true,
		`oba 'kaya' hook "record"`:                                        true,
		`oba kaya hook rec"or"d`:                                          true,
		`oba kaya hook re\cord`:                                           true,
		`sh -c 'oba kaya hook record'`:                                    true,
		`cd "$CLAUDE_PROJECT_DIR" && oba kaya hook record>/dev/null 2>&1`: true,
		"$(command -v oba) kaya hook record":                              true,
		"/usr/local/bin/kaya hook record":                                 true,
		"kaya hook record":                                                true,
		"oba kaya hook session-start":                                     false,
		"oba kaya hook recorder":                                          false,
		"oba kaya statusline":                                             false,
		"oba hook record":                                                 false,
		"oba kayak hook record":                                           false,
		"oba kaya-hook record":                                            false,
		"":                                                                false,
	} {
		if got := RegistersRecordHook(settingsWithHook(t, command)); got != want {
			t.Errorf("RegistersRecordHook(%q) = %v, want %v", command, got, want)
		}
	}
}

func TestRegistersRecordHook_findsTheHookOfEveryDropInAndReadsTheFileAsJSON(t *testing.T) {
	for _, obaPath := range []string{"/opt/homebrew/bin/oba", "/Users/dev/my tools/oba", "/Users/it's/bin/oba"} {
		_, content := renderDropIn(t, obaPath)
		if !RegistersRecordHook(content) {
			t.Errorf("the drop-in of %s does not register the record hook:\n%s", obaPath, content)
		}
	}
	for text, want := range map[string]bool{
		// JSON escapes between the words, which only a JSON reading turns into whitespace.
		`{"hooks": {"Stop": [{"hooks": [{"command": "oba kaya hook\trecord"}]}]}}`: true,
		// Not JSON, for the trailing comma: searched as text.
		`{"hooks": {"Stop": [{"hooks": [{"command": "oba kaya hook record"}]}]},}`:        true,
		`{"hooks": {"Stop": [{"hooks": [{"command": "oba kaya hook session-start"}]}]},}`: false,
		`{"permissions": {"deny": ["Bash(rm -rf /)"]}}`:                                   false,
	} {
		if got := RegistersRecordHook([]byte(text)); got != want {
			t.Errorf("RegistersRecordHook(%s) = %v, want %v", text, got, want)
		}
	}
}
