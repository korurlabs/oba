package board

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// AddChildren makes the boards childRefs name children of the board ref names and returns the
// number added. Each ref is a name, key prefix or id.
func AddChildren(c *client.Client, ref string, childRefs []string) (int, error) {
	var refs []string
	for _, r := range childRefs {
		if r = strings.TrimSpace(r); r != "" {
			refs = append(refs, r)
		}
	}
	if len(refs) == 0 {
		return 0, errors.New("--child-ids is required")
	}
	resolver := NewResolver(c)
	parent, err := resolver.Resolve(ref)
	if err != nil {
		return 0, err
	}
	ids := make([]string, 0, len(refs))
	for _, r := range refs {
		child, err := resolver.Resolve(r)
		if err != nil {
			return 0, err
		}
		ids = append(ids, child.ID)
	}
	if _, err := c.AddChildBoards(parent.ID, ids); err != nil {
		return 0, err
	}
	return len(ids), nil
}
