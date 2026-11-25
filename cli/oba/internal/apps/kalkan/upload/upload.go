// Package upload writes a directory of Markdown files to Kalkan as a tree of pages. It reads the
// directory as a bundle before any call, and finds the committed kalkan.yaml that names the
// organization and the directories to upload, so every developer and agent of a repository
// uploads the same docs to the same place without flags.
package upload

import "strings"

// isFence reports a line, already trimmed, that opens or closes a fenced code block. Nothing
// inside a code block is a title or a link.
func isFence(trimmed string) bool {
	return strings.HasPrefix(trimmed, "```") || strings.HasPrefix(trimmed, "~~~")
}
