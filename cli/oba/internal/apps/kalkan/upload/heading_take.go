package upload

import "strings"

// takeHeading takes the first level-one heading outside a code block out of the body and
// returns its text as the title.
func takeHeading(body string) (string, string) {
	lines := strings.SplitAfter(body, "\n")
	inFence := false
	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if isFence(trimmed) {
			inFence = !inFence
			continue
		}
		if inFence || !strings.HasPrefix(trimmed, "# ") {
			continue
		}
		title := strings.TrimSpace(strings.TrimRight(strings.TrimPrefix(trimmed, "# "), "#"))
		rest := strings.Join(lines[:i], "") + strings.Join(lines[i+1:], "")
		return title, strings.TrimLeft(rest, "\r\n")
	}
	return "", body
}
