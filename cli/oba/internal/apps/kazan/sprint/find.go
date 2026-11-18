package sprint

import (
	"fmt"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Find returns the sprint of board boardID that name names, read without recording it for
// --json. The word "sprint" means the running sprint, else the newest one. A name is matched
// case ignored; when several sprints share it, the running one wins, else the first listed.
func Find(c *client.Client, boardID, name string) (client.Sprint, error) {
	name = strings.TrimSpace(name)
	sprints, err := c.Unrecorded().ListSprints(boardID)
	if err != nil {
		return client.Sprint{}, err
	}
	if len(sprints) == 0 {
		return client.Sprint{}, fmt.Errorf("board %s has no sprints", boardID)
	}
	if strings.EqualFold(name, keywordCurrent) {
		for _, s := range sprints {
			if s.Status == statusActive {
				return s, nil
			}
		}
		return newest(sprints), nil
	}
	var first *client.Sprint
	for i := range sprints {
		if !strings.EqualFold(sprints[i].Name, name) {
			continue
		}
		if sprints[i].Status == statusActive {
			return sprints[i], nil
		}
		if first == nil {
			first = &sprints[i]
		}
	}
	if first != nil {
		return *first, nil
	}
	names := make([]string, len(sprints))
	for i, s := range sprints {
		names[i] = s.Name
	}
	return client.Sprint{}, fmt.Errorf("no sprint of the board is named %q; its sprints: %s", name, strings.Join(names, ", "))
}

// newest returns the sprint created last; a creation time that does not parse counts as oldest.
func newest(sprints []client.Sprint) client.Sprint {
	best := sprints[0]
	bestAt := createdAt(best)
	for _, s := range sprints[1:] {
		if at := createdAt(s); at.After(bestAt) {
			best, bestAt = s, at
		}
	}
	return best
}

func createdAt(s client.Sprint) time.Time {
	at, err := time.Parse(time.RFC3339Nano, s.CreatedAt)
	if err != nil {
		return time.Time{}
	}
	return at
}
