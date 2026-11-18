package board

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

// Selected returns the board a board-scoped command acts on in the organization of c: the one
// flagValue names (the --board flag), else the default board chosen for that organization with
// 'oba kazan board use'. With neither it returns ErrNoBoard.
func Selected(c *client.Client, flagValue string) (Ref, error) {
	if strings.TrimSpace(flagValue) != "" {
		return Resolve(c, flagValue)
	}
	settings, err := config.Load()
	if err != nil {
		return Ref{}, err
	}
	if id := settings.KazanBoards[c.OrgID]; id != "" {
		return Ref{ID: id}, nil
	}
	return Ref{}, ErrNoBoard
}
