package updates

import (
	"fmt"
	"strconv"
	"strings"
)

// lineFlagValue is one --percent or --note value: the ticket it names and what to set.
type lineFlagValue struct {
	ref        string
	percentage int
	note       string
}

// LineFlags are the per-line values given with --percent and --note, in the order they were
// typed.
type LineFlags struct {
	percents []lineFlagValue
	notes    []lineFlagValue
}

// refs lists every ticket the flags name.
func (f LineFlags) refs() []string {
	refs := make([]string, 0, len(f.percents)+len(f.notes))
	for _, p := range f.percents {
		refs = append(refs, p.ref)
	}
	for _, n := range f.notes {
		refs = append(refs, n.ref)
	}
	return refs
}

// Empty reports whether no --percent or --note was given.
func (f LineFlags) Empty() bool {
	return len(f.percents) == 0 && len(f.notes) == 0
}

// ParseLineFlags reads the --percent and --note values, each KEY=VALUE. A malformed value, a
// percentage outside 0-100 or a ticket named twice by the same flag is an error before any
// request. "KEY=" as a note clears it.
func ParseLineFlags(percents, notes []string) (LineFlags, error) {
	var f LineFlags
	seen := map[string]bool{}
	for _, raw := range percents {
		ref, value, err := splitLineFlag("percent", raw)
		if err != nil {
			return f, err
		}
		n, err := ParsePercentage(value)
		if err != nil {
			return f, fmt.Errorf("--percent %s: %w", ref, err)
		}
		if seen[strings.ToLower(ref)] {
			return f, fmt.Errorf("--percent names %s twice", ref)
		}
		seen[strings.ToLower(ref)] = true
		f.percents = append(f.percents, lineFlagValue{ref: ref, percentage: n})
	}

	seen = map[string]bool{}
	for _, raw := range notes {
		ref, value, err := splitLineFlag("note", raw)
		if err != nil {
			return f, err
		}
		if seen[strings.ToLower(ref)] {
			return f, fmt.Errorf("--note names %s twice", ref)
		}
		seen[strings.ToLower(ref)] = true
		f.notes = append(f.notes, lineFlagValue{ref: ref, note: strings.TrimSpace(value)})
	}
	return f, nil
}

// splitLineFlag splits a KEY=VALUE flag value at its first "=".
func splitLineFlag(flag, raw string) (string, string, error) {
	ref, value, found := strings.Cut(raw, "=")
	ref = strings.TrimSpace(ref)
	if !found || ref == "" {
		return "", "", fmt.Errorf("--%s must be KEY=VALUE, such as WEB-218=40, got %q", flag, raw)
	}
	return ref, value, nil
}

// ParsePercentage parses a whole percentage from 0 to 100.
func ParsePercentage(value string) (int, error) {
	n, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || n < MinPercentage || n > MaxPercentage {
		return 0, fmt.Errorf("percentage must be a whole number from %d to %d, got %q", MinPercentage, MaxPercentage, value)
	}
	return n, nil
}
