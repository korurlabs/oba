package member

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// UpdateSpecialties replaces the specialties of the member userRef names, by email or user id, on
// board boardID with the comma-separated list raw. A bad list is refused before any call.
func UpdateSpecialties(c *client.Client, boardID, userRef, raw string) error {
	specialties, err := ParseSpecialties(raw)
	if err != nil {
		return err
	}
	userID, err := ResolveUser(c, userRef)
	if err != nil {
		return err
	}
	return c.SetBoardMemberSpecialties(boardID, userID, specialties)
}
