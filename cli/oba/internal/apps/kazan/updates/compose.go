package updates

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// focusQuestion and questionsQuestion are asked on a terminal for what the flags left open.
const (
	focusQuestion     = "What will you focus on today"
	questionsQuestion = "Questions"
)

// TextFlag is a --focus or --questions value and whether it was given at all, so that an empty
// value given on purpose differs from none.
type TextFlag struct {
	Value   string
	Changed bool
}

// ComposeInput is what a new update is built from: the --tickets refs, the --percent and --note
// values, --focus and --questions. FocusRequired makes the focus question repeat until it is
// answered. Prompter is nil without a terminal, and then nothing is asked.
type ComposeInput struct {
	TicketRefs    []string
	LineFlags     LineFlags
	Focus         TextFlag
	Questions     TextFlag
	FocusRequired bool
	Prompter      *Prompter
	Me            func() (string, error)
}

// Compose chooses the tickets, applies the flags and, on a terminal, asks for what the flags
// left open. A ticket named twice is an error before any write.
func Compose(r *ticket.Resolver, in ComposeInput) (client.DailyUpdateRequest, error) {
	tickets, err := ChooseTickets(r, in.TicketRefs, in.Prompter, in.Me)
	if err != nil {
		return client.DailyUpdateRequest{}, err
	}

	lines := make([]Line, 0, len(tickets))
	seen := map[string]bool{}
	for _, t := range tickets {
		id := strings.ToLower(t.ID)
		if seen[id] {
			return client.DailyUpdateRequest{}, fmt.Errorf("ticket %s is listed twice", t.Key)
		}
		seen[id] = true
		lines = append(lines, lineFromTicket(t))
	}
	if err := ApplyLineFlags(lines, in.LineFlags); err != nil {
		return client.DailyUpdateRequest{}, err
	}

	focus := OptionalText(in.Focus.Value)
	questions := OptionalText(in.Questions.Value)
	if p := in.Prompter; p != nil {
		if err := p.Lines(lines); err != nil {
			return client.DailyUpdateRequest{}, err
		}
		if !in.Focus.Changed {
			if in.FocusRequired {
				focus, err = p.RequiredText(focusQuestion, nil)
			} else {
				focus, err = p.OptionalText(focusQuestion, nil)
			}
			if err != nil {
				return client.DailyUpdateRequest{}, err
			}
		}
		if !in.Questions.Changed {
			if questions, err = p.OptionalText(questionsQuestion, nil); err != nil {
				return client.DailyUpdateRequest{}, err
			}
		}
	}
	return client.DailyUpdateRequest{Tickets: Requests(lines), Focus: focus, Questions: questions}, nil
}
