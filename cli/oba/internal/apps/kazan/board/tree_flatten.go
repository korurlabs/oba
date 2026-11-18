package board

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// FlatBoard is one board of a flattened tree; Depth is 0 for a top-level board and grows by one
// per level below it.
type FlatBoard struct {
	client.Board
	Depth int
}

// Flatten lists the board tree depth first, each board followed by its descendants.
func Flatten(tree []client.Board) []FlatBoard {
	return appendFlat(nil, tree, 0)
}

func appendFlat(flat []FlatBoard, boards []client.Board, depth int) []FlatBoard {
	for _, b := range boards {
		flat = append(flat, FlatBoard{Board: b, Depth: depth})
		flat = appendFlat(flat, b.ChildBoards, depth+1)
	}
	return flat
}

// TreeLabel indents name by depth, so that a child board shows under its parent.
func TreeLabel(name string, depth int) string {
	if depth == 0 {
		return name
	}
	return strings.Repeat("   ", depth-1) + "  └─ " + name
}
