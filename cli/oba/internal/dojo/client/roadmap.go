package client

import (
	"fmt"
	"net/url"
)

// RoadmapBoard is one lane of a roadmap: a board and its milestones, sorted by target date.
type RoadmapBoard struct {
	ID         string      `json:"id"`
	Name       string      `json:"name"`
	Prefix     string      `json:"prefix"`
	Color      *string     `json:"color"`
	Milestones []Milestone `json:"milestones"`
}

// Roadmap is the roadmap of a board: the requested window, YYYY-MM-DD days or nil, and one lane
// per board, the board itself first.
type Roadmap struct {
	From   *string        `json:"from"`
	To     *string        `json:"to"`
	Boards []RoadmapBoard `json:"boards"`
}

// GetRoadmap reads the roadmap of board boardID; query may hold includeChildBoards, from and to.
func (c *Client) GetRoadmap(boardID string, query url.Values) (Roadmap, error) {
	body, err := c.Get(withQuery(boardPath(boardID)+"/roadmap", query))
	if err != nil {
		return Roadmap{}, err
	}
	return parseData[Roadmap](body, fmt.Sprintf("the roadmap of board %s", boardID))
}
