package install

import (
	"encoding/json"
	"errors"
	"fmt"
	"regexp"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

// maxHookGroups caps the hook groups one event of a Claude Code settings file may hold. A real
// file registers a handful per event; the cap keeps a corrupt or hostile file from driving the
// size of the list the CLI allocates for its rewrite.
const maxHookGroups = 1024

// ErrTooManyHookGroups reports a settings file whose event lists more hook groups than
// maxHookGroups.
var ErrTooManyHookGroups = errors.New("too many hook groups")

// withoutKayaGroup returns the hook groups of event without the group whose single hook is
// `oba kaya hook <command>`, or `kaya hook <command>` of the former kaya CLI, so that a run
// replaces Kaya's own registration and keeps every other.
func withoutKayaGroup(hooks *jsonedit.Object, event, command string) ([]json.RawMessage, error) {
	var groups []json.RawMessage
	if raw, ok := hooks.Get(event); ok {
		if err := json.Unmarshal(raw, &groups); err != nil {
			return nil, fmt.Errorf("hooks.%s is not a list: %w", event, err)
		}
	}
	if len(groups) > maxHookGroups {
		return nil, fmt.Errorf("hooks.%s has %d groups, more than %d: %w", event, len(groups), maxHookGroups, ErrTooManyHookGroups)
	}
	ours := regexp.MustCompile(`(^|/)(oba'? kaya|kaya'?) hook ` + regexp.QuoteMeta(command) + `$`)
	kept := make([]json.RawMessage, 0, len(groups)+1)
	for _, raw := range groups {
		var group hookGroup
		if json.Unmarshal(raw, &group) == nil && len(group.Hooks) == 1 && ours.MatchString(group.Hooks[0].Command) {
			continue
		}
		kept = append(kept, raw)
	}
	return kept, nil
}
