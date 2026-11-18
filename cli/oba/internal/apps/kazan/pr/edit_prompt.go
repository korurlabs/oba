package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// PromptEdit asks for the status, note and reviewers, with the current values as defaults. The
// related tickets are kept.
func (e *Edit) PromptEdit(c *client.Client, p *Prompter, me MeFunc) error {
	status, err := PickStatus(p, e.Status, "current")
	if err != nil {
		return err
	}
	e.Status = status

	note, err := p.OptionalText("Note", e.Note)
	if err != nil {
		return err
	}
	if err := CheckNote(note); err != nil {
		return err
	}
	e.Note = note

	current := make([]string, len(e.ReviewerIDs))
	for i, id := range e.ReviewerIDs {
		current[i] = e.Names[strings.ToLower(id)]
	}
	label := "Reviewers: none"
	if len(current) > 0 {
		label = "Reviewers: " + strings.Join(current, ", ")
	}
	_, _ = fmt.Fprintln(p.Out(), label)
	change, err := p.Confirm("Change the reviewers (removing one drops their approval)?")
	if err != nil || !change {
		return err
	}
	ids, names, err := PickReviewers(c, p, me)
	if err != nil {
		return err
	}
	e.ReviewerIDs = ids
	for id, name := range names {
		if _, known := e.Names[id]; !known {
			e.Names[id] = name
		}
	}
	return nil
}
