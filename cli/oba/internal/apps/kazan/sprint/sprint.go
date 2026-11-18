// Package sprint finds the sprints of a Kazan board that commands name, and lists, reads,
// creates, changes, starts, completes, links, unlinks, deletes and restores them.
//
// The board package resolves board names and moves the tickets of a deleted board into sprints,
// so it imports this package; the jobs here that name other boards take a BoardResolver instead.
package sprint

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// statusActive is the API's SprintStatus of the running sprint.
const statusActive = "ACTIVE"

// keywordCurrent, given as a sprint name, means the running sprint of the board, else its newest.
const keywordCurrent = "sprint"

// The --tickets-to values of complete, unlink and delete.
const (
	ticketsToBacklog = "backlog"
	ticketsToSprint  = "sprint"
	ticketsToDelete  = "delete"
)

// BoardResolver returns the id of the board ref names: a name, key prefix or id.
type BoardResolver func(ref string) (string, error)

// Label names a sprint in command output: its name and status.
func Label(s client.Sprint) string {
	return fmt.Sprintf("%s (%s)", guardrail.OneLine(s.Name), s.Status)
}

// orDash is value, or "-" when it is nil.
func orDash(value *string) string {
	if value == nil {
		return "-"
	}
	return guardrail.OneLine(*value)
}
