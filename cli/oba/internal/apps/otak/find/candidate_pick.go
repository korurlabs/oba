package find

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// CountContacts is the number of contacts of the picked candidates (numbers 1-based), for the
// confirmation question.
func CountContacts(candidates []Candidate, picks []int) int {
	total := 0
	for _, number := range picks {
		total += len(candidates[number-1].Contacts)
	}
	return total
}

// Choice is the user's answer at the pick menu: candidate numbers to create, a new search with
// feedback (Refine) or the end of the run (Quit). Exactly one of them is set.
type Choice struct {
	Picks  []int
	Refine bool
	Quit   bool
}

// ParseChoice reads an answer at the pick menu: "r" to search again, "q" to quit, else picks
// as terminal.ParsePicks reads them, case ignored. An empty answer, and picks that select nothing, are
// errors, so that the menu asks again.
func ParseChoice(text string, count int, skipped map[int]bool) (Choice, error) {
	text = strings.TrimSpace(text)
	switch {
	case text == "":
		return Choice{}, errors.New("pick numbers, r or q")
	case strings.EqualFold(text, pickRefine):
		return Choice{Refine: true}, nil
	case strings.EqualFold(text, pickQuit):
		return Choice{Quit: true}, nil
	}
	picks, err := terminal.ParsePicks(text, count, skipped, skippedReason)
	if err != nil {
		return Choice{}, err
	}
	if len(picks) == 0 {
		return Choice{}, errors.New("no candidate can be picked; enter r or q")
	}
	return Choice{Picks: picks}, nil
}
