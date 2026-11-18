package milestone

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// ChangeState closes, reopens or restores the milestone arg names, reachable from board boardID;
// change is client.MilestoneClose, MilestoneReopen or MilestoneRestore. It returns the milestone
// it found.
func ChangeState(c *client.Client, boardID, arg, change string) (client.Milestone, error) {
	m, err := Resolve(c, boardID, arg)
	if err != nil {
		return client.Milestone{}, err
	}
	return m, c.ChangeMilestoneState(owner(m, boardID), m.ID, change)
}
