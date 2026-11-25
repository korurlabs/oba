package cyberscore

import (
	"fmt"
	"slices"
	"strings"
)

// ParseCategory reads a check category with case ignored and "-" or a space read as "_", so
// that "network-edge" names NETWORK_EDGE.
func ParseCategory(raw string) (string, error) {
	return parseEnum("category", raw, Categories)
}

// ParseOutcome reads a check outcome as ParseCategory reads a category.
func ParseOutcome(raw string) (string, error) {
	return parseEnum("outcome", raw, Outcomes)
}

// ParseStatus reads a check status as ParseCategory reads a category, so that "in-progress"
// names IN_PROGRESS.
func ParseStatus(raw string) (string, error) {
	return parseEnum("status", raw, Statuses)
}

func parseEnum(what, raw string, values []string) (string, error) {
	s := strings.ToUpper(strings.TrimSpace(raw))
	s = strings.NewReplacer("-", "_", " ", "_").Replace(s)
	if slices.Contains(values, s) {
		return s, nil
	}
	return "", fmt.Errorf("invalid %s %q: use one of %s", what, raw, strings.Join(values, ", "))
}
