package member

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// UpdateRole gives the member userRef names, by email or user id, the role on board boardID and
// returns the member. An unknown role is refused before any call.
func UpdateRole(c *client.Client, boardID, userRef, role string) (client.BoardMember, error) {
	parsed, err := ParseRole(role)
	if err != nil {
		return client.BoardMember{}, err
	}
	userID, err := ResolveUser(c, userRef)
	if err != nil {
		return client.BoardMember{}, err
	}
	return c.ChangeBoardMemberRole(boardID, userID, parsed)
}
