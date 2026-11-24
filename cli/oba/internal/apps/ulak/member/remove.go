package member

import (
	"errors"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ErrRemoveNeedsYes is printed as it is, so it keeps the capital and the full stop.
var ErrRemoveNeedsYes = errors.New("Pass --yes to remove without a prompt.") //nolint:staticcheck // ST1005: shown to the user verbatim

// Remove removes the member with userID from the client's organization; whether the caller may
// is the API's rule.
func Remove(c *client.Client, userID string) error {
	return c.RemoveMember(userID)
}
