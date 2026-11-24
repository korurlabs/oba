package invite

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ErrRevokeNeedsYes is printed as it is, so it keeps the capital and the full stop.
var ErrRevokeNeedsYes = errors.New("Pass --yes to revoke without a prompt.") //nolint:staticcheck // ST1005: shown to the user verbatim

// Find picks the invitation id from invs, case ignored, so that the confirmation can name the
// e-mail. ok is false when the list has no such id.
func Find(invs []client.Invitation, id string) (client.Invitation, bool) {
	for _, inv := range invs {
		if strings.EqualFold(inv.ID, id) {
			return inv, true
		}
	}
	return client.Invitation{}, false
}

// Revoke revokes the pending invitation id. An unknown id is the API's 404 and an invitation
// no longer pending its 422.
func Revoke(c *client.Client, id string) error {
	return c.RevokeInvitation(id)
}
