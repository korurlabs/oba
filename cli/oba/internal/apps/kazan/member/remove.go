package member

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Remove removes the member with userID from board boardID. The caller resolves the user first
// with ResolveUser, so that a confirmation can name the member before anything is removed.
func Remove(c *client.Client, boardID, userID string) error {
	return c.RemoveBoardMember(boardID, userID)
}
