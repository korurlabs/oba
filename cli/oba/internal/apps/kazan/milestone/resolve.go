package milestone

import (
	"errors"
	"fmt"
	"net/url"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// errNoMilestone refuses a command that names no milestone. A picker is not offered: the command
// must run the same way in a script and on a terminal.
var errNoMilestone = errors.New("milestone name or id is required")

// Reachable reads the milestones a ticket of board boardID can belong to, of every status: the
// board's own and those of its related boards. The lookup stays out of --json.
func Reachable(c *client.Client, boardID string) ([]client.Milestone, error) {
	return c.Unrecorded().ListMilestones(boardID, url.Values{"includeRelatedBoards": {"true"}})
}

// Resolve finds the milestone arg names among those reachable from board boardID: an id, also
// one that is not listed, such as a deleted milestone being restored, which is then taken to be
// on boardID; else the one milestone with that name, case ignored.
func Resolve(c *client.Client, boardID, arg string) (client.Milestone, error) {
	if strings.TrimSpace(arg) == "" {
		return client.Milestone{}, errNoMilestone
	}
	milestones, err := Reachable(c, boardID)
	if err != nil {
		return client.Milestone{}, err
	}
	return match(milestones, boardID, arg)
}

// match picks the milestone arg names. A name several milestones share is refused with every
// match and its id, since only the id tells them apart.
func match(milestones []client.Milestone, boardID, arg string) (client.Milestone, error) {
	arg = strings.TrimSpace(arg)
	if arg == "" {
		return client.Milestone{}, errNoMilestone
	}
	if userinput.IsID(arg) {
		for _, m := range milestones {
			if strings.EqualFold(m.ID, arg) {
				return m, nil
			}
		}
		return client.Milestone{ID: arg, Name: arg, BoardID: boardID}, nil
	}
	var matches []client.Milestone
	for _, m := range milestones {
		if strings.EqualFold(m.Name, arg) {
			matches = append(matches, m)
		}
	}
	switch len(matches) {
	case 1:
		return matches[0], nil
	case 0:
		if len(milestones) == 0 {
			return client.Milestone{}, fmt.Errorf("milestone %q not found: no milestones on this board or its related boards", arg)
		}
		names := make([]string, len(milestones))
		for i, m := range milestones {
			names[i] = guardrail.OneLine(m.Name)
		}
		return client.Milestone{}, fmt.Errorf("milestone %q not found. Available milestones: %s", arg, strings.Join(names, ", "))
	default:
		lines := make([]string, len(matches))
		for i, m := range matches {
			lines[i] = fmt.Sprintf("  %s  %s  target %s  board %s", m.ID, guardrail.OneLine(m.Name), m.TargetDate, BoardLabel(m))
		}
		return client.Milestone{}, fmt.Errorf("%d milestones are named %q; pass the milestone id instead:\n%s",
			len(matches), arg, strings.Join(lines, "\n"))
	}
}
