package member

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// ResolveUser returns the user id of the member ref names: a user id as it is, an email matched
// against the members of the organization of c. A blank ref is refused, since it names nobody.
func ResolveUser(c *client.Client, ref string) (string, error) {
	if strings.TrimSpace(ref) == "" {
		return "", errors.New("a user email or user ID is required")
	}
	ids, err := organization.ResolveMemberIDs(c, []string{ref})
	if err != nil {
		return "", err
	}
	return ids[0], nil
}
