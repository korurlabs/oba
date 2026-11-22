package install

import (
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
