// Package milestone lists, reads, creates, changes, closes, reopens, deletes and restores the
// milestones of a Kazan board, attaches tickets to them and records the dependencies between
// them. Milestone names are not unique, so a milestone is named by its id or by a name only one
// milestone has.
package milestone

import (
	"fmt"
	"regexp"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// colorPattern is the color format the API accepts, as for a board's color.
var colorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

// CheckColor refuses a --color value that is not #RRGGBB, before any call.
func CheckColor(value string) error {
	if !colorPattern.MatchString(value) {
		return fmt.Errorf("--color must be a hex colour in #RRGGBB format, got %q", value)
	}
	return nil
}

// Label names a milestone in command output with its id, because another milestone can carry
// the same name.
func Label(m client.Milestone) string {
	if m.Name == "" || m.Name == m.ID {
		return m.ID
	}
	return fmt.Sprintf("%q (%s)", guardrail.OneLine(m.Name), m.ID)
}

// BoardLabel names the board a milestone belongs to.
func BoardLabel(m client.Milestone) string {
	switch {
	case m.BoardName != "" && m.BoardPrefix != "":
		return fmt.Sprintf("%s (%s)", guardrail.OneLine(m.BoardName), guardrail.OneLine(m.BoardPrefix))
	case m.BoardName != "":
		return guardrail.OneLine(m.BoardName)
	default:
		return m.BoardID
	}
}

// owner is the board a milestone is addressed under: its own, which can be a related board of
// the one the command acts on.
func owner(m client.Milestone, boardID string) string {
	if m.BoardID != "" {
		return m.BoardID
	}
	return boardID
}
