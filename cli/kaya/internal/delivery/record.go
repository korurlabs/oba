package delivery

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Release states a session reports; the server stores them on the session.
const (
	// StateFresh: the session runs on the newest release the server named.
	StateFresh = "FRESH"
	// StateCached: Kaya was not reached, or its newest release could not be verified; the
	// session runs on the last verified release.
	StateCached = "CACHED"
	// StateStale: a newer release is verified and waits for a restart (a live session of the
	// checkout pins the active one, or the newer one changes always-on files).
	StateStale = "STALE"
	// StateNone: no verified release on this machine; the guard refuses tools.
	StateNone = "NONE"
	// StateUnverified: the active release no longer verifies, or a changed file could not be
	// restored; the guard refuses tools.
	StateUnverified = "UNVERIFIED"
)

// How the release reached the session.
const (
	DeliveredByLauncher = "LAUNCHER"
	DeliveredByHook     = "HOOK"
)

// Report is summary.configRelease of the recorder's ingest batch: {number, manifestSha256, state,
// deliveredBy}, number and manifestSha256 null without a release.
type Report struct {
	Number         *int    `json:"number"`
	ManifestSHA256 *string `json:"manifestSha256"`
	State          string  `json:"state"`
	DeliveredBy    string  `json:"deliveredBy"`
}

// NewReport builds the report of an outcome.
func NewReport(number int, manifestSHA256, state, deliveredBy string) Report {
	report := Report{State: state, DeliveredBy: deliveredBy}
	if number > 0 && state != StateNone {
		n := number
		sha := strings.ToLower(manifestSHA256)
		report.Number = &n
		report.ManifestSHA256 = &sha
	}
	return report
}

// RecorderStateDir is the recorder's state directory, $XDG_STATE_HOME/kaya (~/.local/state/kaya).
func RecorderStateDir() (string, error) {
	base := os.Getenv("XDG_STATE_HOME")
	if base == "" {
		home, err := os.UserHomeDir()
		if err != nil {
			return "", fmt.Errorf("find the home directory: %w", err)
		}
		base = filepath.Join(home, ".local", "state")
	}
	return filepath.Join(base, "kaya"), nil
}

// WriteReport puts the session's release report where the recorder picks it up:
// queue/sessions/<session id>/config-release.json of its state directory, by rename.
func WriteReport(sessionID string, report Report) error {
	if !ValidSessionID(sessionID) {
		return fmt.Errorf("session id %q is not valid", sessionID)
	}
	base, err := RecorderStateDir()
	if err != nil {
		return err
	}
	dir := filepath.Join(base, "queue", "sessions", sessionID)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create %s: %w", dir, err)
	}
	data, err := json.Marshal(report)
	if err != nil {
		return fmt.Errorf("encode the release report: %w", err)
	}
	return writeFileAtomic(filepath.Join(dir, "config-release.json"), append(data, '\n'), 0o600)
}
