package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// reviewerRoles are the organization roles a reviewer may hold; a GUEST cannot review.
var reviewerRoles = map[string]bool{"OWNER": true, "ADMIN": true, "MEMBER": true}

// PickReviewers lets the user pick reviewers among the organization's active OWNERs, ADMINs and
// MEMBERs, the caller left out. It returns the user ids and, by lower-case id, the names shown.
// The member read is a lookup and stays out of the --json output.
func PickReviewers(c *client.Client, p *Prompter, me MeFunc) ([]string, map[string]string, error) {
	self, err := me()
	if err != nil {
		return nil, nil, err
	}
	members, err := c.Unrecorded().ListMembers()
	if err != nil {
		return nil, nil, err
	}
	var candidates []client.Member
	for _, m := range members {
		if m.Status == organization.MemberStatusActive && reviewerRoles[m.Role] && !strings.EqualFold(m.UserID, self) {
			candidates = append(candidates, m)
		}
	}
	if len(candidates) == 0 {
		_, _ = fmt.Fprintln(p.Out(), "No other member can review.")
		return []string{}, map[string]string{}, nil
	}
	options := make([]string, len(candidates))
	for i, m := range candidates {
		options[i] = fmt.Sprintf("%s <%s>", memberName(m), guardrail.OneLine(m.Email))
	}
	indexes, err := p.ChooseMany(fmt.Sprintf("Reviewers (at most %d)", MaxReviewers), options)
	if err != nil {
		return nil, nil, err
	}
	if len(indexes) > MaxReviewers {
		return nil, nil, fmt.Errorf("you picked %d reviewers; a share can name at most %d", len(indexes), MaxReviewers)
	}
	ids := make([]string, len(indexes))
	names := map[string]string{}
	for i, idx := range indexes {
		ids[i] = candidates[idx].UserID
		names[strings.ToLower(ids[i])] = memberName(candidates[idx])
	}
	return ids, names, nil
}

// memberName is a member's full name, or the e-mail without one.
func memberName(m client.Member) string {
	first, last := "", ""
	if m.FirstName != nil {
		first = *m.FirstName
	}
	if m.LastName != nil {
		last = *m.LastName
	}
	return guardrail.PersonName(first, last, m.Email)
}
