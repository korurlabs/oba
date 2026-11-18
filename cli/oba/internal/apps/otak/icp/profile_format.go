package icp

import (
	"strings"
)

// SizeRange renders a company size range for people: "any", "at least X", "at most Y" or
// "X to Y".
func SizeRange(minSize, maxSize *string) string {
	switch {
	case minSize == nil && maxSize == nil:
		return "any"
	case maxSize == nil:
		return "at least " + *minSize
	case minSize == nil:
		return "at most " + *maxSize
	default:
		return *minSize + " to " + *maxSize
	}
}

func valueOr(value *string, fallback string) string {
	if value == nil || strings.TrimSpace(*value) == "" {
		return fallback
	}
	return *value
}
