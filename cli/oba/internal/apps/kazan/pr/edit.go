package pr

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// EditFlags are the change flags of an edit as given, checked before any request. A list flag
// that replaces cannot be combined with the flags that add to or remove from that list.
type EditFlags struct {
	Status          string
	Note            *string
	NoteGiven       bool
	ReplaceReviewer bool
	Reviewers       []string
	AddReviewers    []string
	RemoveReviewers []string
	ReplaceTickets  bool
	Tickets         []string
	AddTickets      []string
	RemoveTickets   []string
}

// ErrNoChanges refuses an edit without a change flag where nobody can be asked for the changes.
var ErrNoChanges = errors.New("no changes specified: pass --status, --note, --reviewers, --add-reviewers, " +
	"--remove-reviewers, --tickets, --add-tickets or --remove-tickets")

// Check refuses a replacing list flag combined with an add or remove flag of the same list.
func (f EditFlags) Check() error {
	if f.ReplaceReviewer && (len(f.AddReviewers) > 0 || len(f.RemoveReviewers) > 0) {
		return errors.New("--reviewers replaces the list: do not combine it with --add-reviewers or --remove-reviewers")
	}
	if f.ReplaceTickets && (len(f.AddTickets) > 0 || len(f.RemoveTickets) > 0) {
		return errors.New("--tickets replaces the list: do not combine it with --add-tickets or --remove-tickets")
	}
	return nil
}

// HasChanges reports whether any change flag was given.
func (f EditFlags) HasChanges() bool {
	return f.Status != "" || f.NoteGiven || f.ReplaceReviewer || len(f.AddReviewers) > 0 ||
		len(f.RemoveReviewers) > 0 || f.ReplaceTickets || len(f.AddTickets) > 0 || len(f.RemoveTickets) > 0
}

// Edit is a share's editable content while it is changed: the status, the note, the reviewer
// ids and the related ticket ids in display order, with a name per reviewer id and a label per
// ticket id, both keyed by the lower-case id.
type Edit struct {
	Status       string
	Note         *string
	ReviewerIDs  []string
	Names        map[string]string
	TicketIDs    []string
	TicketLabels map[string]string
}

// EditOf copies a share's editable content, its reviewers named as the card names them and its
// related tickets labelled by their stored keys.
func EditOf(s client.PullRequestShare) Edit {
	e := Edit{
		Status: s.Status, Note: s.Note,
		ReviewerIDs: make([]string, 0, len(s.Reviewers)), Names: map[string]string{},
		TicketIDs: make([]string, 0, len(s.RelatedTickets)), TicketLabels: map[string]string{},
	}
	for _, r := range s.Reviewers {
		e.ReviewerIDs = append(e.ReviewerIDs, r.UserID)
		e.Names[strings.ToLower(r.UserID)] = guardrail.PersonName(r.FirstName, r.LastName, r.UserID)
	}
	for _, t := range s.RelatedTickets {
		e.TicketIDs = append(e.TicketIDs, t.TicketID)
		label := t.TicketKey
		if label == "" {
			label = t.TicketID
		}
		e.TicketLabels[strings.ToLower(t.TicketID)] = label
	}
	return e
}

// Apply applies the change flags to e, a copy of share current. Every refusal comes before any
// write.
func (e *Edit) Apply(c *client.Client, current client.PullRequestShare, f EditFlags, me MeFunc) error {
	if f.Status != "" {
		e.Status = f.Status
	}
	if f.NoteGiven {
		e.Note = f.Note
	}
	if err := e.applyReviewers(c, f, me); err != nil {
		return err
	}
	return e.applyTickets(c, current.RelatedTickets, f)
}

// Request is the body that saves e. The pull request of a share never changes, so it is not
// sent.
func (e Edit) Request() client.PullRequestShareRequest {
	return client.PullRequestShareRequest{
		Status:           e.Status,
		Note:             e.Note,
		ReviewerIDs:      e.ReviewerIDs,
		RelatedTicketIDs: e.TicketIDs,
	}
}
