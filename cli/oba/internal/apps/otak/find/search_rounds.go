package find

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// MaxSearches bounds the Claude Code runs of one find. Each run searches the web anew and
// carries the whole history in its brief, so every further search costs more on the user's
// Claude account.
const MaxSearches = 5

// round is one search of a find: the candidates Claude Code returned, and the feedback the user
// gave on them, if any.
type round struct {
	candidates []Candidate
	feedback   string
}

// Search is one find, kept in memory only: Claude Code runs without a session, so every search
// gets the ICP, the focus and all earlier candidates and feedback in its brief.
type Search struct {
	icp       client.Icp
	relations []client.RelationListItem
	focus     string
	rounds    []round
}

// NewSearch starts a find for the ICP, keeping the known relations out of it. focus is what
// the user wrote to narrow the search; empty means none.
func NewSearch(icp client.Icp, relations []client.RelationListItem, focus string) *Search {
	return &Search{icp: icp, relations: relations, focus: strings.TrimSpace(focus)}
}

// Searches is the number of searches recorded so far.
func (s *Search) Searches() int {
	return len(s.rounds)
}

// AddRound records the candidates of a search.
func (s *Search) AddRound(candidates []Candidate) {
	s.rounds = append(s.rounds, round{candidates: candidates})
}

// AddFeedback records the user's feedback on the latest search. Feedback before any search
// opens a round without candidates, so that it still reaches the next brief.
func (s *Search) AddFeedback(text string) {
	if len(s.rounds) == 0 {
		s.rounds = append(s.rounds, round{})
	}
	s.rounds[len(s.rounds)-1].feedback = strings.TrimSpace(text)
}
