// Package pr shares GitHub pull requests in the organization's PR channel, lists and reads the
// shares, edits and deletes the caller's own and approves the ones the caller reviews. It also
// lists the repositories Kazan's GitHub App can read and their pull requests.
package pr

import (
	"fmt"
	"strings"
	"unicode/utf16"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// Limits the API enforces, counted as the API counts them (UTF-16 units), so that a breach is
// refused before any request.
const (
	MaxNoteLength     = 500
	MaxReviewers      = 10
	MaxRelatedTickets = 10
	MaxSearchLength   = 100
	MaxPullAuthors    = 5
	MaxSharePageSize  = 50
	DefaultSharePage  = 1
	DefaultShareCount = 25

	// PullPageSize is GitHub's page size of a repository's pull requests.
	PullPageSize = 30
)

// Statuses are the team's review statuses of a share, in the order the web app offers them.
var Statuses = []string{"DRAFT", "OPEN", "IN_REVIEW", "MERGED", "DISCUSS"}

// statusLabels are the labels the card shows.
var statusLabels = map[string]string{
	"DRAFT":     "Draft",
	"OPEN":      "Open",
	"IN_REVIEW": "In Review",
	"MERGED":    "Merged",
	"DISCUSS":   "Discuss",
}

// PullStates are GitHub's states of a pull request, as the pull request filter takes them. They
// are not the team's review statuses of a share.
var PullStates = []string{"OPEN", "DRAFT", "MERGED", "CLOSED"}

// StatusLabel is the card label of a status, or the status as given.
func StatusLabel(status string) string {
	if label, ok := statusLabels[status]; ok {
		return label
	}
	return guardrail.OneLine(status)
}

// ShareID checks a share id before any request: shares have no key, so only a UUID names one.
func ShareID(arg string) (string, error) {
	id := strings.TrimSpace(arg)
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid share ID %q: use the ID that 'oba kazan pr list' prints", arg)
	}
	return id, nil
}

// utf16Length counts a text as the API does: Java's String.length() counts UTF-16 units, so an
// emoji counts twice.
func utf16Length(s string) int {
	return len(utf16.Encode([]rune(s)))
}
