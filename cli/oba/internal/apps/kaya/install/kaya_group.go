package install

import (
	"encoding/json"
	"errors"
	"fmt"
	"path"
	"regexp"
	"strings"
	"unicode"

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

// shellQuoting removes the quotes and backslashes of a command line, which only group and escape
// characters for the shell and leave the words it runs as they are.
var shellQuoting = strings.NewReplacer(`'`, "", `"`, "", `\`, "")

// wordEnds end a word of a command line besides whitespace: the shell's control operators,
// redirections, subshells and command substitutions, and the JSON punctuation that a settings file
// searched as text keeps around its strings.
const wordEnds = ";&|()<>`{}[],"

// RegistersRecordHook reports whether a Claude Code settings file runs Kaya's record hook from any
// of its strings. The drop-in writes `<oba path> kaya hook record`, with the path quoted for the
// shell where it needs it; a file written by hand may run the same command through a bare oba,
// with other whitespace, with quoted words or inside `sh -c '...'`, and the former kaya CLI ran
// `kaya hook record`. A file that is not JSON is searched as text rather than taken to register
// nothing.
func RegistersRecordHook(settings []byte) bool {
	var value any
	if err := json.Unmarshal(settings, &value); err != nil {
		// rule-10-no-op -- reason: the file is searched as text instead, which errs towards finding the hook.
		return runsRecordHook(string(settings))
	}
	return holdsRecordHook(value)
}

// holdsRecordHook reports whether a string anywhere in value, a decoded JSON value, runs Kaya's
// record hook.
func holdsRecordHook(value any) bool {
	switch v := value.(type) {
	case string:
		return runsRecordHook(v)
	case []any:
		for _, item := range v {
			if holdsRecordHook(item) {
				return true
			}
		}
	case map[string]any:
		for _, item := range v {
			if holdsRecordHook(item) {
				return true
			}
		}
	}
	return false
}

// runsRecordHook reports whether the command line command runs Kaya's record hook: the words
// `kaya hook record`, the first one also as the last part of a path, as oba's subcommand or as
// the former kaya CLI. The quotes and backslashes are removed before the line is split into words,
// which also opens a command nested in `sh -c '...'`. That errs towards a match, such as for the
// one word `hook\ record`: a hook this misses would let its record event pass for the drop-in's.
func runsRecordHook(command string) bool {
	words := strings.FieldsFunc(shellQuoting.Replace(command), func(r rune) bool {
		return unicode.IsSpace(r) || strings.ContainsRune(wordEnds, r)
	})
	for i := 0; i+2 < len(words); i++ {
		if path.Base(words[i]) == "kaya" && words[i+1] == "hook" && words[i+2] == recordCommand {
			return true
		}
	}
	return false
}
