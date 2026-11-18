package userinput

import "strings"

// SplitCSV splits a comma separated value into its trimmed, non-blank entries, in order.
func SplitCSV(csv string) []string {
	var values []string
	for _, raw := range strings.Split(csv, ",") {
		if v := strings.TrimSpace(raw); v != "" {
			values = append(values, v)
		}
	}
	return values
}
