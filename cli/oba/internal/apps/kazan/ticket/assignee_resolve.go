package ticket

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// assigneeIDs resolves the comma-separated --assignee of a ticket on board boardID, keeping the
// order given. A user id names a person or an agent as peopleIDs resolves it. An email is first
// matched against the board's agents and otherwise resolved by peopleIDs as a person's: the
// organization member lookup leaves agents out, and 'oba kazan member list' shows an agent's email,
// so a copied agent email has to resolve here. Any other value is the name of an agent among the
// board's members: an agent's email is a generated address nobody types, so a name is the usual way
// to name one. An empty list is nil.
func assigneeIDs(c *client.Client, boardID, csv string) ([]string, error) {
	refs := userinput.SplitCSV(csv)
	if len(refs) == 0 {
		return nil, nil
	}
	var members []client.BoardMember
	for _, ref := range refs {
		if userinput.IsID(ref) {
			continue
		}
		var err error
		if members, err = c.Unrecorded().ListBoardMembers(boardID); err != nil {
			return nil, fmt.Errorf("--assignee: failed to read the board members to find agent %q: %w",
				guardrail.OneLine(ref), err)
		}
		break
	}
	ids := make([]string, len(refs))
	var people []string
	var peopleAt []int
	for i, ref := range refs {
		if userinput.IsID(ref) {
			people = append(people, ref)
			peopleAt = append(peopleAt, i)
			continue
		}
		if strings.Contains(ref, "@") {
			if agent, ok := agentByEmail(members, ref); ok {
				ids[i] = agent.UserID
				continue
			}
			people = append(people, ref)
			peopleAt = append(peopleAt, i)
			continue
		}
		agent, err := findAgent(members, ref)
		if err != nil {
			return nil, fmt.Errorf("--assignee: %w", err)
		}
		ids[i] = agent.UserID
	}
	personIDs, err := peopleIDs(c, "assignee", strings.Join(people, ","))
	if err != nil {
		return nil, err
	}
	for n, i := range peopleAt {
		ids[i] = personIDs[n]
	}
	return ids, nil
}

// agentByEmail returns the agent among members whose email is email, compared without regard to
// case. A person's email is not matched here: people resolve through the organization lookup.
func agentByEmail(members []client.BoardMember, email string) (client.BoardMember, bool) {
	for _, m := range members {
		if m.IsAgent() && strings.EqualFold(m.Email, email) {
			return m, true
		}
	}
	return client.BoardMember{}, false
}

// findAgent returns the one agent among members whose name is name, compared without regard to
// case or repeated spaces. A name no agent has, or one that two agents share, is refused with the
// candidates, so the caller can retype the name or fall back to a user id.
func findAgent(members []client.BoardMember, name string) (client.BoardMember, error) {
	wanted := guardrail.OneLine(name)
	var agents, matches []client.BoardMember
	for _, m := range members {
		if !m.IsAgent() {
			continue
		}
		agents = append(agents, m)
		if strings.EqualFold(agentName(m), wanted) {
			matches = append(matches, m)
		}
	}
	switch {
	case len(matches) == 1:
		return matches[0], nil
	case len(matches) > 1:
		return client.BoardMember{}, fmt.Errorf("%d agents on the board are named %q: %s; name one by its user id",
			len(matches), wanted, agentList(matches))
	case len(agents) == 0:
		return client.BoardMember{}, &client.APIError{Status: http.StatusNotFound, Message: fmt.Sprintf(
			"no agent named %q: the board has no agents; name a person by email or user id", wanted)}
	default:
		return client.BoardMember{}, &client.APIError{Status: http.StatusNotFound, Message: fmt.Sprintf(
			"no agent named %q; agents on the board: %s; name a person by email or user id", wanted, agentList(agents))}
	}
}

// agentName is the name of an agent as 'oba kazan member list' shows it.
func agentName(m client.BoardMember) string {
	return guardrail.OneLine(m.FirstName + " " + m.LastName)
}

// agentList names each agent with its user id, so an agent whose name is shared can still be named.
func agentList(agents []client.BoardMember) string {
	parts := make([]string, 0, len(agents))
	for _, a := range agents {
		parts = append(parts, fmt.Sprintf("%s (%s)", agentName(a), guardrail.OneLine(a.UserID)))
	}
	return strings.Join(parts, ", ")
}
