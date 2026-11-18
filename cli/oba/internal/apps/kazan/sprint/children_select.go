package sprint

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// ErrChildFlagsConflict refuses --link-children with --child-boards: the command cannot tell
// which set of boards the user means.
var ErrChildFlagsConflict = fmt.Errorf("--link-children and --child-boards cannot be used together")

// LinkableChildren lists the child boards of boardID that a new sprint is linked to by default,
// as the web dialog checks them: the user may link them, and they run no active sprint of their
// own that the shared sprint would compete with. The lookup stays out of --json.
func LinkableChildren(c *client.Client, boardID string) ([]client.SprintChildBoard, error) {
	candidates, err := c.Unrecorded().ListSprintChildBoards(boardID)
	if err != nil {
		return nil, fmt.Errorf("failed to list child boards: %w", err)
	}
	var out []client.SprintChildBoard
	for _, b := range candidates {
		if b.CanLink && b.ActiveSprintID == nil {
			out = append(out, b)
		}
	}
	return out, nil
}

// ChildIDs returns the ids of boards.
func ChildIDs(boards []client.SprintChildBoard) []string {
	ids := make([]string, len(boards))
	for i, b := range boards {
		ids[i] = b.ID
	}
	return ids
}

// ChildrenQuestion names every board a yes links, so the user does not confirm a set they cannot
// see.
func ChildrenQuestion(boards []client.SprintChildBoard) string {
	names := make([]string, len(boards))
	for i, b := range boards {
		names[i] = fmt.Sprintf("%s (%s)", guardrail.OneLine(b.Name), guardrail.OneLine(b.Prefix))
	}
	return "Also use this sprint on child boards " + strings.Join(names, ", ") + "?"
}
