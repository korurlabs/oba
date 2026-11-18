package milestone

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// UnlinkTickets detaches the tickets of change from its milestone, reachable from board boardID.
// It returns the milestone with its new progress and the number of tickets sent.
func UnlinkTickets(c *client.Client, boardID string, change TicketsChange) (client.Milestone, int, error) {
	return changeTickets(c, boardID, change, c.UnlinkMilestoneTickets)
}
