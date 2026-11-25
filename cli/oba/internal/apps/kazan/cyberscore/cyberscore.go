// Package cyberscore reads the cyberscore of the organization, its category breakdown and its
// trend, and lists, reads, creates, changes and deletes the checks behind it. The score belongs to
// the organization, not to a board. A check is named by its id or by its name, which is unique
// in the organization.
package cyberscore

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Categories are the check categories in the API's declaration order, which is also the order of
// the breakdown and of the check list.
var Categories = []string{
	"IDENTITY", "OFFICE", "NETWORK_EDGE", "CLOUD_PLATFORM", "APPLICATIONS",
	"DATA", "DEVICES", "DEVELOPMENT_SUPPLY_CHAIN", "DETECTION_RESPONSE", "COMPLIANCE",
}

// Outcomes are the results a check can record. TODO and NOT_APPLICABLE do not count toward the
// score.
var Outcomes = []string{"TODO", "PASS", "PARTIAL", "FAIL", "NO_LICENSE", "NOT_APPLICABLE"}

// Statuses are the work states of a check.
var Statuses = []string{"NOT_STARTED", "IN_PROGRESS", "DONE", "NOT_APPLICABLE"}

// The limits of the API's check fields, checked before any call so that a script sees the
// mistake without a round trip. The server stays the authority.
const (
	maxNameLength = 255
	maxTextLength = 5000
	minWeight     = 1
	maxWeight     = 100
	minSortOrder  = 0
	maxSortOrder  = 9999
	minTrendDays  = 1
	maxTrendDays  = 365
)

// Label names a check in command output with its id.
func Label(ch client.CyberscoreCheck) string {
	if ch.Name == "" {
		return ch.ID
	}
	return fmt.Sprintf("%q (%s)", guardrail.OneLine(ch.Name), ch.ID)
}

// orDash is a nullable value for a table cell or a labelled line.
func orDash(value *string) string {
	if value == nil || *value == "" {
		return "-"
	}
	return guardrail.OneLine(*value)
}
