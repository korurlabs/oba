package updates

import (
	"errors"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// errNoChanges is the error of an edit without a change flag and without a terminal to ask on.
var errNoChanges = errors.New("no changes specified: pass --add, --remove, --percent, --note, --focus or --questions")

// EditInput is what an edit changes: the --add and --remove refs, the --percent and --note
// values, --focus and --questions. Without any of them the edit asks on a terminal for every
// line, the focus and the questions.
type EditInput struct {
	AddRefs    []string
	RemoveRefs []string
	LineFlags  LineFlags
	Focus      TextFlag
	Questions  TextFlag
}

// HasChanges reports whether any change flag was given.
func (in EditInput) HasChanges() bool {
	return len(in.AddRefs) > 0 || len(in.RemoveRefs) > 0 || !in.LineFlags.Empty() || in.Focus.Changed || in.Questions.Changed
}

// Check validates the input before any request: without a terminal (interactive false) a change
// flag is needed, and a given focus may not be blank.
func (in EditInput) Check(interactive bool) error {
	if !in.HasChanges() && !interactive {
		return errNoChanges
	}
	if in.Focus.Changed && OptionalText(in.Focus.Value) == nil {
		return ErrEditFocusEmpty
	}
	return nil
}

// EditRequest builds the full new content of update current from the input. On a terminal (p
// not nil) and without a change flag it asks for every line, the focus and the questions, with
// the current values as defaults. An update left without a ticket line or without a focus is an
// error before any write.
func EditRequest(r *ticket.Resolver, current client.DailyUpdate, in EditInput, p *Prompter) (client.DailyUpdateRequest, error) {
	lines, err := editedLines(r, current, in)
	if err != nil {
		return client.DailyUpdateRequest{}, err
	}
	focus := current.Focus
	if in.Focus.Changed {
		focus = OptionalText(in.Focus.Value)
	}
	questions := current.Questions
	if in.Questions.Changed {
		questions = OptionalText(in.Questions.Value)
	}
	if p != nil && !in.HasChanges() {
		if err := p.Lines(lines); err != nil {
			return client.DailyUpdateRequest{}, err
		}
		if focus, err = p.RequiredText(focusQuestion, focus); err != nil {
			return client.DailyUpdateRequest{}, err
		}
		if questions, err = p.OptionalText(questionsQuestion, questions); err != nil {
			return client.DailyUpdateRequest{}, err
		}
	}
	if BlankText(focus) {
		return client.DailyUpdateRequest{}, ErrEditFocusMissing
	}
	return client.DailyUpdateRequest{Tickets: Requests(lines), Focus: focus, Questions: questions}, nil
}

// editedLines rebuilds the update's lines from its items and applies --remove, --add, --percent
// and --note. A line whose ticket is gone has no ticket id to send; the server keeps it at its
// place.
func editedLines(r *ticket.Resolver, current client.DailyUpdate, in EditInput) ([]Line, error) {
	lines := make([]Line, 0, len(current.Items)+len(in.AddRefs))
	for _, item := range current.Items {
		if item.TicketID == nil || *item.TicketID == "" {
			continue
		}
		lines = append(lines, Line{
			TicketID:   *item.TicketID,
			Key:        item.TicketKey,
			Title:      item.TicketTitle,
			Percentage: item.Percentage,
			Note:       item.Note,
		})
	}

	for _, ref := range in.RemoveRefs {
		i := findLine(lines, ref)
		if i < 0 {
			return nil, fmt.Errorf("--remove %s: the ticket is not on the update (tickets: %s)", ref, lineKeys(lines))
		}
		lines = append(lines[:i], lines[i+1:]...)
	}

	for _, ref := range in.AddRefs {
		t, err := ResolveTicket(r, ref)
		if err != nil {
			return nil, err
		}
		if findLine(lines, t.ID) >= 0 {
			return nil, fmt.Errorf("--add %s: the ticket is already on the update", ref)
		}
		lines = append(lines, lineFromTicket(t))
	}

	if err := ApplyLineFlags(lines, in.LineFlags); err != nil {
		return nil, err
	}
	if len(lines) == 0 {
		return nil, errors.New("an update keeps at least one ticket: use 'oba kazan updates delete' to remove it")
	}
	return lines, nil
}
