// Package invite creates, lists and revokes the invitations of the current organization. An
// invitation is how a person joins: they accept it in the account app, and only then do they
// appear among the members.
package invite

import (
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const emptyCell = "-"

// cell is value on one line with every terminal control removed, or "-" when nothing is left.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}

// localDay is the calendar day of the RFC 3339 instant in loc, or "" when there is none. A value
// that does not parse is shown as the API sent it, on one line, rather than hidden.
func localDay(instant *string, loc *time.Location) string {
	if instant == nil {
		return ""
	}
	t, err := time.Parse(time.RFC3339, *instant)
	if err != nil {
		return guardrail.OneLine(*instant)
	}
	return t.In(loc).Format(time.DateOnly)
}
