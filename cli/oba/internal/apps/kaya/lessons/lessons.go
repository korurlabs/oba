// Package lessons holds what the oba kaya lessons commands decide locally: the limits Kaya would
// refuse, which session records a lesson, and how a lessons file is imported.
package lessons

import (
	"fmt"
	"strings"
	"unicode/utf16"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

const (
	maxTitle = 300
	maxBody  = 4000
	// maxSearch is the longest search Kaya takes.
	maxSearch = 200
)

// length counts characters as Java counts them, the way Kaya checks its limits.
func length(text string) int {
	return len(utf16.Encode([]rune(text)))
}

// CheckTitle refuses a title Kaya would refuse, and one that spans lines: a title is one line of
// the derived lessons file and of the session context.
func CheckTitle(title string) error {
	if strings.TrimSpace(title) == "" {
		return fmt.Errorf("the title is empty; state the rule as a sentence, 1 to %d characters", maxTitle)
	}
	if strings.ContainsAny(title, "\r\n") {
		return fmt.Errorf("the title %q spans lines; state the rule as one sentence", title)
	}
	if size := length(title); size > maxTitle {
		return fmt.Errorf("the title has %d characters; Kaya takes at most %d", size, maxTitle)
	}
	return nil
}

// CheckBody refuses a text Kaya would refuse.
func CheckBody(body string) error {
	if strings.TrimSpace(body) == "" {
		return fmt.Errorf("the text is empty; say what went wrong, why, and the correct approach, 1 to %d characters", maxBody)
	}
	if size := length(body); size > maxBody {
		return fmt.Errorf("the text has %d characters; Kaya takes at most %d", size, maxBody)
	}
	return nil
}

// CheckSearch refuses a search Kaya would refuse.
func CheckSearch(search string) error {
	if strings.TrimSpace(search) == "" {
		return fmt.Errorf("the search is empty; name a topic, 1 to %d characters", maxSearch)
	}
	if size := length(search); size > maxSearch {
		return fmt.Errorf("the search has %d characters; Kaya takes at most %d", size, maxSearch)
	}
	return nil
}

// SessionOf picks the session a lesson belongs to from the checkout's live sessions: the one of
// the Claude Code process that runs the command, else the only one that has a session id. Empty
// when neither names a session, since a guess would link the lesson to the wrong session.
func SessionOf(claudePID int, live []delivery.Session) string {
	var named []string
	for _, session := range live {
		if !delivery.ValidSessionID(session.SessionID) {
			continue
		}
		if session.PID == claudePID {
			return session.SessionID
		}
		named = append(named, session.SessionID)
	}
	if len(named) == 1 {
		return named[0]
	}
	return ""
}
