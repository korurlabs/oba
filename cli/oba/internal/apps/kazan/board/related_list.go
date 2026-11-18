package board

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// ListRelated reads the boards related to the board ref names in its board tree.
func ListRelated(c *client.Client, ref string) ([]client.Board, error) {
	named, err := Resolve(c, ref)
	if err != nil {
		return nil, err
	}
	return c.ListRelatedBoards(named.ID)
}
