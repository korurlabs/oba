package pr

import (
	"fmt"
	"strings"
)

// ParseStatus reads a share status with case ignored and "-" or a space read as "_", so that
// "in-review" and "In Review" both name IN_REVIEW.
func ParseStatus(raw string) (string, error) {
	s := strings.ToUpper(strings.TrimSpace(raw))
	s = strings.NewReplacer("-", "_", " ", "_").Replace(s)
	if _, ok := statusLabels[s]; ok {
		return s, nil
	}
	return "", fmt.Errorf("invalid status %q: use one of %s", raw, strings.Join(Statuses, ", "))
}

// PresetStatus maps GitHub's state to the status a new share starts with. A closed pull request
// has no preset: the author must choose.
func PresetStatus(githubState string) (string, bool) {
	switch githubState {
	case "DRAFT", "OPEN", "MERGED":
		return githubState, true
	default:
		return "", false
	}
}
