// Package comment lists, adds, changes and deletes the comments of a Kazan ticket.
package comment

import (
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	// previewRunes is how much of a comment a table or a pick list shows.
	previewRunes = 60

	emptyCell = "-"
)

// preview puts a comment on one line and cuts it to previewRunes, ending a cut text with "...".
func preview(content string) string {
	runes := []rune(guardrail.OneLine(content))
	if len(runes) <= previewRunes {
		return string(runes)
	}
	return string(runes[:previewRunes-3]) + "..."
}

// author is the comment author's full name, the email when the name is empty.
func author(first, last, email string) string {
	name := guardrail.PersonName(first, last, email)
	if name == "" {
		return emptyCell
	}
	return name
}
