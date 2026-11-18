// Package updates writes and reads Kazan daily updates. An update lists tickets with a
// percentage and an optional note, then what the author will focus on and their questions; the
// API renders it as paste-ready text. A percentage that differs from the ticket's progress also
// becomes the ticket's progress when the author is OWNER, ADMIN or MEMBER on its board.
package updates

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Limits the server enforces. The percentage range and the page size are checked before any
// request, so that a typo never reaches the API.
const (
	MinPercentage = 0
	MaxPercentage = 100
	MaxPageSize   = 50
	DefaultPage   = 1
	DefaultAmount = 25
)

// OpenTicketStatuses are the statuses offered when the tickets of an update are picked by hand:
// a DONE ticket is not today's work.
var OpenTicketStatuses = []string{"TODO", "IN_PROGRESS", "WAITING_FOR_THIRD_PARTY", "IN_REVIEW"}

// The server refuses a create or edit without a focus, but its error names no field, so the CLI
// checks the focus itself first.
var (
	ErrCreateFocusRequired = errors.New("--focus is required: what you will focus on today")
	ErrEditFocusEmpty      = errors.New("--focus cannot be empty")
	ErrEditFocusMissing    = errors.New("this update has no focus: pass --focus")
)

// ErrCancelled is returned when the user leaves a question or a pick list without an answer.
var ErrCancelled = errors.New("cancelled")

// Line is one ticket line while an update is composed. PercentSet and NoteSet record that a flag
// gave the value, so the questions on a terminal skip it.
type Line struct {
	TicketID   string
	Key        string
	Title      string
	Percentage int
	Note       *string
	PercentSet bool
	NoteSet    bool
}

// matches reports whether ref names this line: its ticket key, case ignored, or its ticket id.
func (l Line) matches(ref string) bool {
	ref = strings.TrimSpace(ref)
	return strings.EqualFold(ref, l.Key) || strings.EqualFold(ref, l.TicketID)
}

// lineFromTicket starts a line from a ticket, with the ticket's progress as the percentage.
func lineFromTicket(t client.Ticket) Line {
	return Line{TicketID: t.ID, Key: t.Key, Title: t.Title, Percentage: t.Percentage}
}

// Requests turns the composed lines into request lines, in order.
func Requests(lines []Line) []client.DailyUpdateTicketRequest {
	out := make([]client.DailyUpdateTicketRequest, len(lines))
	for i, l := range lines {
		out[i] = client.DailyUpdateTicketRequest{TicketID: l.TicketID, Percentage: l.Percentage, Note: l.Note}
	}
	return out
}

// OptionalText turns a typed text into a request field: blank means none.
func OptionalText(value string) *string {
	v := strings.TrimSpace(value)
	if v == "" {
		return nil
	}
	return &v
}

// BlankText reports whether a stored or typed text holds nothing to show.
func BlankText(value *string) bool {
	return value == nil || strings.TrimSpace(*value) == ""
}

// cell is an API text as one terminal-safe line.
func cell(value string) string {
	return guardrail.OneLine(value)
}
