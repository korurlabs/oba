package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// MeFunc returns the user id of the signed-in user; it reads it on its first call only.
type MeFunc func() (string, error)

// ResolveReviewers resolves reviewers given by email, matched among the ACTIVE members only, or
// by user id to user ids, in the order given; the result is aligned with refs and never nil,
// since the API requires the list. Two entries naming the same user, or the caller, are refused:
// the author cannot review their own share.
func ResolveReviewers(c *client.Client, refs []string, me MeFunc) ([]string, error) {
	if len(refs) == 0 {
		return []string{}, nil
	}
	ids, err := organization.ResolveActiveMemberIDs(c, refs)
	if err != nil {
		return nil, err
	}
	self, err := me()
	if err != nil {
		return nil, err
	}
	for i, id := range ids {
		if strings.EqualFold(id, self) {
			return nil, fmt.Errorf("reviewer %s is you: you cannot review your own share", refs[i])
		}
		if at := indexOfID(ids[:i], id); at >= 0 {
			return nil, fmt.Errorf("reviewers %s and %s are the same user", refs[at], refs[i])
		}
	}
	return ids, nil
}
