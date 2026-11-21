package delivery

import (
	"fmt"
	"io"
)

// Summary is the one-line state of a checkout's release, as the commands and the session start
// hook print it.
func Summary(checkout *Checkout, out Outcome, state State) string {
	name := checkout.Name(state)
	switch out.State {
	case StateNone:
		return fmt.Sprintf("No Kaya config on this machine for %s. Run `oba kaya sync` online once.", name)
	case StateUnverified:
		return fmt.Sprintf("The Kaya config of %s does not verify; tools are refused until `oba kaya sync` restores it.", name)
	}
	line := fmt.Sprintf("%s: config release %d (%s)", name, out.Active, out.State)
	if out.ActiveCreatedAt != "" {
		line += " of " + out.ActiveCreatedAt
	}
	return line
}

// Details are the lines that explain a state beyond the summary.
func Details(out Outcome) []string {
	var lines []string
	if out.Switched && out.Previous > 0 {
		lines = append(lines, fmt.Sprintf("Switched from release %d.", out.Previous))
	}
	if out.Staged > out.Active {
		switch {
		case out.AlwaysOnStaged:
			lines = append(lines, fmt.Sprintf("Config release %d changes always-on rules; restart Claude Code to use it.", out.Staged))
		case len(out.LiveSessions) > 0:
			lines = append(lines, fmt.Sprintf("Config release %d waits until the %d other sessions of this checkout end (or run `oba kaya refresh`).", out.Staged, len(out.LiveSessions)))
		default:
			lines = append(lines, fmt.Sprintf("Config release %d is staged for the next session.", out.Staged))
		}
	}
	if out.Offline != nil && out.Active > 0 {
		lines = append(lines, fmt.Sprintf("Kaya is unreachable or its newest release was not taken (%v); this session runs on config release %d of %s.", out.Offline, out.Active, out.ActiveCreatedAt))
	} else if out.Offline != nil {
		lines = append(lines, fmt.Sprintf("Kaya was not reached: %v", out.Offline))
	}
	for _, problem := range out.Restored {
		lines = append(lines, "Restored "+problem.String())
	}
	for _, problem := range out.Problems {
		lines = append(lines, "Not restored: "+problem.String())
	}
	for _, warning := range out.Warnings {
		lines = append(lines, "Warning: "+warning)
	}
	return lines
}

// PrintOutcome prints the summary and the details, each line after prefix.
func PrintOutcome(w io.Writer, prefix string, checkout *Checkout, out Outcome, state State) {
	_, _ = fmt.Fprintln(w, prefix+Summary(checkout, out, state))
	for _, line := range Details(out) {
		_, _ = fmt.Fprintln(w, prefix+line)
	}
}

// SessionName names a live session: its Claude Code session id, else the launcher that started it.
func SessionName(s Session) string {
	if s.SessionID != "" {
		return s.SessionID
	}
	return "started by oba kaya launch"
}

// OutcomeView is the --json result of oba kaya sync, refresh and status.
type OutcomeView struct {
	Checkout       string    `json:"checkout"`
	Repository     string    `json:"repository"`
	State          string    `json:"state"`
	Active         int       `json:"active,omitempty"`
	CreatedAt      string    `json:"createdAt,omitempty"`
	ManifestSHA256 string    `json:"manifestSha256,omitempty"`
	Latest         int       `json:"latest,omitempty"`
	Staged         int       `json:"staged,omitempty"`
	CheckedAt      string    `json:"checkedAt,omitempty"`
	Switched       bool      `json:"switched,omitempty"`
	Offline        string    `json:"offline,omitempty"`
	LiveSessions   []Session `json:"liveSessions"`
	Problems       []string  `json:"problems"`
	Warnings       []string  `json:"warnings"`
}

// NewOutcomeView is the --json result of an outcome; its lists are empty, never null.
func NewOutcomeView(checkout *Checkout, out Outcome, state State) OutcomeView {
	view := OutcomeView{
		Checkout: checkout.Root, Repository: checkout.Name(state), State: out.State, Active: out.Active,
		CreatedAt: out.ActiveCreatedAt, ManifestSHA256: out.ManifestSHA256, Latest: out.Latest, Staged: out.Staged,
		CheckedAt: state.CheckedAt, Switched: out.Switched, LiveSessions: out.LiveSessions,
		Problems: []string{}, Warnings: out.Warnings,
	}
	if view.LiveSessions == nil {
		view.LiveSessions = []Session{}
	}
	if view.Warnings == nil {
		view.Warnings = []string{}
	}
	if out.Offline != nil {
		view.Offline = out.Offline.Error()
	}
	for _, problem := range out.Problems {
		view.Problems = append(view.Problems, problem.String())
	}
	return view
}
