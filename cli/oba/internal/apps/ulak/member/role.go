package member

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// ChangeRole gives the member with userID the role, which role.ParseRole has checked. Every
// other rule (own role, an admin changing an owner, the caller's own role below ADMIN) is the
// API's.
func ChangeRole(c *client.Client, userID, role string) (client.Member, error) {
	return c.ChangeMemberRole(userID, role)
}
