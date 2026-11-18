package organization

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// MemberStatusActive is the status of a membership whose user can still act in the
// organization.
const MemberStatusActive = "ACTIVE"

// unnamedOrganization names the organization in a not-found message when its name is not at hand.
const unnamedOrganization = "the organization"

// ResolveMemberIDs returns the user id of each ref, in order, skipping blank refs: a user id as
// it is, anything else matched with FindMember against the members of the organization of c.
// The members are read once, on the first ref that is not a user id, and kept out of the --json
// output, since the read is a lookup and not the command's result.
func ResolveMemberIDs(c *client.Client, refs []string) ([]string, error) {
	return resolveMemberIDs(c, refs, false)
}

// ResolveActiveMemberIDs resolves refs as ResolveMemberIDs does, but matches an e-mail only
// among the ACTIVE members, for a role that only a user who can still act may take, such as a
// reviewer. A user id is still used as it is; the API refuses one that cannot take the role.
func ResolveActiveMemberIDs(c *client.Client, refs []string) ([]string, error) {
	return resolveMemberIDs(c, refs, true)
}

func resolveMemberIDs(c *client.Client, refs []string, activeOnly bool) ([]string, error) {
	ids := make([]string, 0, len(refs))
	var members []client.Member
	loaded := false
	for _, raw := range refs {
		ref := strings.TrimSpace(raw)
		if ref == "" {
			continue
		}
		if userinput.IsID(ref) {
			ids = append(ids, ref)
			continue
		}
		if !loaded {
			list, err := c.Unrecorded().ListMembers()
			if err != nil {
				return nil, err
			}
			if activeOnly {
				list = active(list)
			}
			members, loaded = list, true
		}
		m, err := FindMember(members, unnamedOrganization, ref)
		if err != nil {
			return nil, err
		}
		ids = append(ids, m.UserID)
	}
	return ids, nil
}

// active keeps the members whose membership is ACTIVE.
func active(members []client.Member) []client.Member {
	kept := make([]client.Member, 0, len(members))
	for _, m := range members {
		if m.Status == MemberStatusActive {
			kept = append(kept, m)
		}
	}
	return kept
}
