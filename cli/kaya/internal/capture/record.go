// Package capture turns one Claude Code hook event into the local queue the sender sends to
// Kaya: the event with its per-session seq, the session's summary and facts, and the plan and
// handoff snapshots the event shows.
//
// A hook call must never get in Claude Code's way: it reads no network, prints nothing on
// stdout, and on tool events runs no git process. Only sessions of a checkout that holds a
// committed kaya.yaml are recorded, so a session in another repository never reaches Kaya.
package capture

import (
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"time"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

const (
	// senderRestartSeconds is the age at which a sender.started file without a token report
	// means the sender failed to report, which the token warning names.
	senderRestartSeconds = 30
	// SenderStartedFile is written by the hook each time it starts a sender.
	SenderStartedFile = "sender.started"
)

// rescanTools and rescanEvents trigger a look at the handoff directories the session's briefs
// named: a handoff can be written by a shell command (a heredoc, sed, cp), which names no file.
var (
	rescanTools  = map[string]bool{"Bash": true, "Agent": true, "Task": true}
	rescanEvents = map[string]bool{subagentStop: true, stop: true}
)

// Decode parses the hook event Claude Code passes on stdin. Numbers keep their digits.
func Decode(raw []byte) (map[string]any, error) {
	var value any
	if err := decodeNumbers(raw, &value); err != nil {
		return nil, fmt.Errorf("the event on stdin is not JSON: %w", err)
	}
	in, ok := value.(map[string]any)
	if !ok {
		return nil, errors.New("the event is not a JSON object")
	}
	return in, nil
}

// Record queues one hook event for Kaya. queued is false when the event's checkout holds no
// kaya.yaml, which is not an error. now stamps the event. Problems that cost a part of the
// record but not the event (a snapshot, a git call) go to warnings as one line each; an error
// means the event was not queued.
func Record(in map[string]any, now time.Time, warnings io.Writer) (queued bool, err error) {
	warn := func(format string, args ...any) {
		_, _ = fmt.Fprintf(warnings, "kaya recorder: "+format+"\n", args...)
	}
	sessionID := sessionIDOf(in)
	if sessionID == "" {
		return false, errors.New("the event has no session_id")
	}
	if !delivery.ValidSessionID(sessionID) {
		return false, fmt.Errorf("the session id %q is not valid", sessionID)
	}
	cwd, err := eventDir(in)
	if err != nil {
		return false, err
	}
	checkout, err := delivery.FindCheckout(cwd)
	if err != nil {
		return false, err
	}
	switch checkout.Kind {
	case delivery.NotRepository:
		return false, fmt.Errorf("%s is not inside a git repository", cwd)
	case delivery.NotKaya:
		return false, nil
	}
	root, err := mainCheckout(cwd, warn)
	if err != nil {
		return false, err
	}
	if root == "" {
		return false, fmt.Errorf("%s is not inside a git repository", cwd)
	}

	event := eventFrom(in, now.UTC().Format(isoSeconds))
	name := stringOf(event["event"])
	if branchEvents[name] {
		where := ExistingDir(cwd)
		if where == "" {
			where = root
		}
		if branch := git(where, warn, "rev-parse", "--abbrev-ref", "HEAD"); branch != "" {
			event["branch"] = branch
		}
	}
	if model := eventModel(in); model != "" {
		event["model"] = model
	}
	if err := enqueue(sessionID, event, in, cwd, root, now, warn); err != nil {
		return false, fmt.Errorf("not queued for Kaya: %w", err)
	}
	if name == sessionStart {
		if err := tokenWarning(sessionID, now, warn); err != nil {
			warn("the sender's token state is unknown: %v", err)
		}
	}
	return true, nil
}

// eventDir is the session's directory: the event's cwd, else CLAUDE_PROJECT_DIR, else the
// hook's own working directory.
func eventDir(in map[string]any) (string, error) {
	if value := in["cwd"]; truthy(value) {
		cwd, ok := value.(string)
		if !ok {
			return "", errors.New("the event's cwd is not a string")
		}
		return cwd, nil
	}
	if dir := os.Getenv("CLAUDE_PROJECT_DIR"); dir != "" {
		return dir, nil
	}
	wd, err := os.Getwd()
	if err != nil {
		return "", fmt.Errorf("read the working directory: %w", err)
	}
	return wd, nil
}

// enqueue adds the event to the session's queue with the next seq, under the session's lock,
// and brings the summary, the snapshots and session.json up to date.
func enqueue(sessionID string, event, in map[string]any, cwd, root string, now time.Time, warn func(string, ...any)) error {
	stateDir, err := EnsureStateDir()
	if err != nil {
		return err
	}
	dir := SessionDir(stateDir, sessionID)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create %s: %w", dir, err)
	}
	unlock, err := LockSession(dir)
	if err != nil {
		return err
	}
	defer unlock()
	seq, err := lastSeq(filepath.Join(dir, EventsFile))
	if err != nil {
		return err
	}
	event["seq"] = seq + 1
	if err := apply(dir, sessionID, event, in, cwd, root, now, warn); err != nil {
		return err
	}
	return updateSession(dir, sessionID, event, in, cwd, root, warn)
}

// apply appends the event, snapshots its handoffs and plans and writes summary.json. The caller
// holds the session's lock.
func apply(dir, sessionID string, event, in map[string]any, cwd, root string, now time.Time, warn func(string, ...any)) error {
	summary, err := loadSummary(dir, sessionID, warn)
	if err != nil {
		return err
	}
	if err := appendEvent(dir, event); err != nil {
		return err
	}
	name, tool := stringOf(event["event"]), stringOf(event["tool_name"])
	if name == postToolUse && writeTools[tool] {
		if _, err := snapshotHandoff(dir, stringOf(event["file_path"]), cwd); err != nil {
			warn("handoff %s not snapshotted: %v", stringOf(event["file_path"]), err)
		}
	}
	summary = fold(summary, event, sessionID)
	if _, err := snapshotPlan(dir, in, root, cwd, summary.StartedAt, now, warn); err != nil {
		warn("plan not captured: %v", err)
	}
	rescan := rescanEvents[name] || (isPostEvent(name) && rescanTools[tool])
	if rescan && len(summary.BriefedFeatures) > 0 {
		since := ""
		if summary.StartedAt != nil {
			since = *summary.StartedAt
		}
		addFeatures(summary, refreshHandoffs(dir, root, summary.BriefedFeatures, since, warn))
	}
	return writeJSON(filepath.Join(dir, SummaryFile), summary)
}

// tokenWarning warns once per session, on SessionStart, when the sender reported that it has no
// usable token or never reported at all, so the session is recorded on this machine only.
func tokenWarning(sessionID string, now time.Time, warn func(string, ...any)) error {
	stateDir, err := delivery.RecorderStateDir()
	if err != nil {
		return err
	}
	marker := filepath.Join(SessionDir(stateDir, sessionID), warnedFile)
	if _, err := os.Stat(marker); err == nil {
		return nil
	} else if !errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("inspect %s: %w", marker, err)
	}
	var state map[string]any
	if _, err := readJSON(filepath.Join(stateDir, StateFile), &state); err != nil {
		// rule-10-no-op -- reason: a damaged state file says nothing about the token, as a missing one does not; the sender rewrites it.
		state = nil
	}
	token, reported := state["token"]
	if token == "ok" {
		return nil
	}
	if !reported || token == nil {
		info, err := os.Stat(filepath.Join(stateDir, SenderStartedFile))
		if err != nil || now.Sub(info.ModTime()) <= senderRestartSeconds*time.Second {
			return nil
		}
	}
	reason := fmt.Sprintf("the sender has not reported (see %s)", filepath.Join(stateDir, LogFile))
	switch token {
	case "missing":
		reason = "no KAYA_RECORDER_TOKEN"
	case "refused":
		reason = "Kaya refused KAYA_RECORDER_TOKEN"
	}
	warn("%s: this session is recorded on this machine only and sent once a valid token is set "+
		"(kagi secrets set --personal --path /kaya/recorder KAYA_RECORDER_TOKEN=<token>)", reason)
	if err := os.WriteFile(marker, nil, 0o600); err != nil {
		return fmt.Errorf("write %s: %w", marker, err)
	}
	return nil
}
