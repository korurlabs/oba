package sprint

import (
	"errors"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// errNoTargetBoards refuses a link that names no board, before any call.
var errNoTargetBoards = errors.New("--to-board is required: the boards to link the sprint to (names, prefixes or ids, comma separated)")

// Link links the sprint of board boardID that name names to the boards of toBoards, a comma
// separated list of names, prefixes or ids that boards resolves. It returns the sprint and the
// ids of the linked boards.
func Link(c *client.Client, boardID, name, toBoards string, boards BoardResolver) (client.Sprint, []string, error) {
	refs := userinput.SplitCSV(toBoards)
	if len(refs) == 0 {
		return client.Sprint{}, nil, errNoTargetBoards
	}
	found, err := Find(c, boardID, name)
	if err != nil {
		return client.Sprint{}, nil, err
	}
	ids, err := resolveBoards(refs, boards)
	if err != nil {
		return client.Sprint{}, nil, err
	}
	return found, ids, c.LinkSprint(boardID, found.ID, client.SprintBulkLinkRequest{BoardIDs: ids})
}

// ResolveBoards returns the ids of the boards csv names, a comma separated list; blank entries
// are skipped.
func ResolveBoards(csv string, boards BoardResolver) ([]string, error) {
	return resolveBoards(userinput.SplitCSV(csv), boards)
}

func resolveBoards(refs []string, boards BoardResolver) ([]string, error) {
	ids := make([]string, 0, len(refs))
	for _, ref := range refs {
		id, err := boards(ref)
		if err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, nil
}
