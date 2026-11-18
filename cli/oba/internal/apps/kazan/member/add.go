package member

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// AddInput is the user to add, by email or user id, and the board role to give.
type AddInput struct {
	User string
	Role string
}

// Add adds the user to board boardID and returns the new member. A missing user or role, or an
// unknown role, is refused before any call.
func Add(c *client.Client, boardID string, in AddInput) (client.BoardMember, error) {
	if strings.TrimSpace(in.User) == "" || strings.TrimSpace(in.Role) == "" {
		return client.BoardMember{}, errors.New("--user and --role are required")
	}
	role, err := ParseRole(in.Role)
	if err != nil {
		return client.BoardMember{}, err
	}
	userID, err := ResolveUser(c, in.User)
	if err != nil {
		return client.BoardMember{}, err
	}
	return c.AddBoardMember(boardID, client.BoardMemberRequest{UserID: userID, Role: role})
}
