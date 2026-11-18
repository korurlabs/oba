package board

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Locate finds board id at any depth of tree and returns it with its parent, nil for a
// top-level board; found is false when the tree does not hold it.
func Locate(tree []client.Board, id string) (node client.Board, parent *client.Board, found bool) {
	return locateIn(tree, nil, id)
}

func locateIn(boards []client.Board, parent *client.Board, id string) (client.Board, *client.Board, bool) {
	for i := range boards {
		if strings.EqualFold(boards[i].ID, id) {
			return boards[i], parent, true
		}
		if node, p, ok := locateIn(boards[i].ChildBoards, &boards[i], id); ok {
			return node, p, true
		}
	}
	return client.Board{}, nil, false
}
