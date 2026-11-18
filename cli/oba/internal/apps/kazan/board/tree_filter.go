package board

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Filter keeps the boards whose name or key prefix contains search, case ignored, at any depth
// of the tree. A board that does not match stays when one of its descendants matches, so that a
// match still shows under its parents; branches without a match are dropped.
func Filter(tree []client.Board, search string) []client.Board {
	needle := strings.ToLower(strings.TrimSpace(search))
	if needle == "" {
		return tree
	}
	return filterLevel(tree, needle)
}

func filterLevel(boards []client.Board, needle string) []client.Board {
	var kept []client.Board
	for _, b := range boards {
		children := filterLevel(b.ChildBoards, needle)
		if strings.Contains(strings.ToLower(b.Name), needle) || strings.Contains(strings.ToLower(b.Prefix), needle) ||
			len(children) > 0 {
			b.ChildBoards = children
			kept = append(kept, b)
		}
	}
	return kept
}

// EncodeTree returns each board of a filtered tree as the API sent it, with its childBoards
// replaced by the kept children, so that --json shows the filtered tree with every field. The
// keys of a board come out in alphabetical order; the values are unchanged.
func EncodeTree(tree []client.Board) ([]json.RawMessage, error) {
	out := make([]json.RawMessage, 0, len(tree))
	for _, b := range tree {
		var fields map[string]json.RawMessage
		if err := json.Unmarshal(b.Raw, &fields); err != nil {
			return nil, fmt.Errorf("failed to read board %s: %w", b.ID, err)
		}
		children, err := EncodeTree(b.ChildBoards)
		if err != nil {
			return nil, err
		}
		encodedChildren, err := json.Marshal(children)
		if err != nil {
			return nil, fmt.Errorf("failed to encode the child boards of %s: %w", b.ID, err)
		}
		fields["childBoards"] = encodedChildren
		encoded, err := json.Marshal(fields)
		if err != nil {
			return nil, fmt.Errorf("failed to encode board %s: %w", b.ID, err)
		}
		out = append(out, encoded)
	}
	return out, nil
}
