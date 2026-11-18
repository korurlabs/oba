package organization

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// MeRef is the entry of an author list that names the signed-in user.
const MeRef = "me"

// ResolveAuthorIDs resolves an author filter: "me", in any case, is the signed-in user, read
// with me, and every other entry is an e-mail or a user id that ResolveMemberIDs resolves. The
// signed-in user's id comes first.
func ResolveAuthorIDs(c *client.Client, refs []string, me func() (string, error)) ([]string, error) {
	var ids, others []string
	for _, raw := range refs {
		ref := strings.TrimSpace(raw)
		if !strings.EqualFold(ref, MeRef) {
			others = append(others, ref)
			continue
		}
		id, err := me()
		if err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	resolved, err := ResolveMemberIDs(c, others)
	if err != nil {
		return nil, err
	}
	return append(ids, resolved...), nil
}
