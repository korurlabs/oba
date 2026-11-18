package board

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

// Use makes the board ref names the default board of the organization of c and returns it. The
// board is read first, so that only a board the user is a member of is stored; its id is stored,
// so that a rename does not lose the choice.
func Use(c *client.Client, ref string) (client.Board, error) {
	named, err := Resolve(c, ref)
	if err != nil {
		return client.Board{}, err
	}
	b, err := c.GetBoard(named.ID)
	if err != nil {
		return client.Board{}, err
	}
	settings, err := config.Load()
	if err != nil {
		return client.Board{}, err
	}
	if settings.KazanBoards == nil {
		settings.KazanBoards = map[string]string{}
	}
	settings.KazanBoards[c.OrgID] = b.ID
	if err := config.Save(settings); err != nil {
		return client.Board{}, err
	}
	return b, nil
}
