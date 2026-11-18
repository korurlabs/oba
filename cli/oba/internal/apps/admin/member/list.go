package member

import (
	"fmt"
	"io"
	"sort"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// roleRank orders the table by authority, owners first; a role the CLI does not know sorts last.
var roleRank = map[string]int{"OWNER": 0, "ADMIN": 1, "MEMBER": 2, "GUEST": 3}

// List reads every member of the client's organization, owners first, then ADMIN, MEMBER and
// GUEST, each role by e-mail with case ignored.
func List(c *client.Client) ([]client.Member, error) {
	members, err := c.ListMembers()
	if err != nil {
		return nil, err
	}
	sort.SliceStable(members, func(i, j int) bool {
		ri, rj := rank(members[i].Role), rank(members[j].Role)
		if ri != rj {
			return ri < rj
		}
		return strings.ToLower(members[i].Email) < strings.ToLower(members[j].Email)
	})
	return members, nil
}

func rank(role string) int {
	if r, ok := roleRank[role]; ok {
		return r
	}
	return len(roleRank)
}

// PrintList writes the members as a NAME, EMAIL, ROLE, STATUS, USER ID table in the given
// order, or "No members." when there are none.
func PrintList(out io.Writer, members []client.Member) error {
	if len(members) == 0 {
		_, _ = fmt.Fprintln(out, "No members.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tEMAIL\tROLE\tSTATUS\tUSER ID")
	for _, m := range members {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n", name(m), cell(m.Email), cell(m.Role), cell(m.Status), cell(m.UserID))
	}
	return w.Flush()
}

// name is "firstName lastName", either part alone when the other is unset or blank, or "-".
func name(m client.Member) string {
	var parts []string
	for _, value := range []*string{m.FirstName, m.LastName} {
		if value == nil {
			continue
		}
		if clean := guardrail.OneLine(*value); clean != "" {
			parts = append(parts, clean)
		}
	}
	if len(parts) == 0 {
		return emptyCell
	}
	return strings.Join(parts, " ")
}
