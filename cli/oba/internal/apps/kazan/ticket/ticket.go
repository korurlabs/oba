// Package ticket lists, reads, creates, changes, deletes, restores, moves and orders the tickets
// of Kazan boards. A ticket is named by its key, such as OPS-42, whose prefix names its board, or
// by its id, whose board is --board or the default board.
package ticket

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// The values of the API's ticket enums. A flag value outside them fails before any request, so
// that a typo never reaches the API as a 400 or as a filter that silently matches nothing.
var (
	Statuses     = []string{"TODO", "IN_PROGRESS", "WAITING_FOR_THIRD_PARTY", "IN_REVIEW", "DONE"}
	Types        = []string{"BUG", "FEATURE", "TECH_DEBT", "IMPROVEMENT", typeIncident, "PROBLEM", "REFACTOR"}
	Priorities   = []string{"LOWEST", "LOW", "MEDIUM", "HIGH", "HIGHEST", "P1", "P2", "P3"}
	MissionRanks = []string{"XS", "S", "M", "L", "XL", "XXL"}
	sortOrders   = []string{"ASC", "DESC"}
)

// AttachmentEntityType is the StorageEntityType of a ticket, the parent of its attachments.
const AttachmentEntityType = "TICKET"

// typeIncident is the one type that uses the P1 to P3 priority scale; every other type uses
// LOWEST to HIGHEST.
const typeIncident = "INCIDENT"

// TASK was replaced by FEATURE. Create and update still take it, so that scripts written for the
// old type keep working, but send FEATURE. The list filter does not take it, since no stored
// ticket has that type any more.
const (
	typeTaskRetired = "TASK"
	typeFeature     = "FEATURE"
)

// EnumValue matches value, case ignored, against allowed and returns the allowed spelling.
func EnumValue(flag, value string, allowed []string) (string, error) {
	v := strings.TrimSpace(value)
	for _, a := range allowed {
		if strings.EqualFold(v, a) {
			return a, nil
		}
	}
	return "", fmt.Errorf("invalid --%s value %q: allowed values are %s", flag, value, strings.Join(allowed, ", "))
}

// enumList is EnumValue for a comma-separated list; blank entries are skipped.
func enumList(flag, csv string, allowed []string) ([]string, error) {
	var values []string
	for _, raw := range userinput.SplitCSV(csv) {
		v, err := EnumValue(flag, raw, allowed)
		if err != nil {
			return nil, err
		}
		values = append(values, v)
	}
	return values, nil
}

// TypeValue reads a --type of create or update like EnumValue and turns the retired TASK into
// FEATURE; retired reports that it did, so that the caller can warn.
func TypeValue(value string) (typ string, retired bool, err error) {
	if strings.EqualFold(strings.TrimSpace(value), typeTaskRetired) {
		return typeFeature, true, nil
	}
	typ, err = EnumValue("type", value, Types)
	return typ, false, err
}

// cell is an API text as one terminal-safe line.
func cell(value string) string {
	return guardrail.OneLine(value)
}

// text is an API text of several lines, such as a description, made terminal-safe with its line
// breaks kept.
func text(value string) string {
	return guardrail.Sanitize(value)
}
