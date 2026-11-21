package capture

import (
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"path/filepath"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

// windowSpan is how far from now a window's reset may lie. Claude's longest window is seven
// days; a reset further away is no usage limit Kaya can show.
const windowSpan = 8 * 24 * time.Hour

// usageWindows maps the status line's rate_limits keys to the usage file's keys.
var usageWindows = []struct{ Input, Key string }{
	{Input: "five_hour", Key: "fiveHour"},
	{Input: "seven_day", Key: "sevenDay"},
}

// UsageDir is queue/usage of the state directory: one file per session with the latest usage
// limits the status line saw, which the sender adds to the session's next batch.
func UsageDir(stateDir string) string {
	return filepath.Join(stateDir, "queue", "usage")
}

// UsagePath is the usage file of one session.
func UsagePath(stateDir, sessionID string) string {
	return filepath.Join(UsageDir(stateDir), sessionID+".json")
}

// CaptureUsage writes the usage file of the session in the status line input in:
// {sessionId, capturedAt, fiveHour?, sevenDay?}, each window {usedPercentage, resetsAt}. It
// returns the path written, or "" when the input holds no session or no valid window, or when
// the windows equal the file's, so an unchanged usage is not sent again. now stamps capturedAt
// and bounds resetsAt.
func CaptureUsage(in map[string]any, now time.Time) (string, error) {
	sessionID, ok := in["session_id"].(string)
	limits := asObject(in["rate_limits"])
	if !ok || !delivery.ValidSessionID(sessionID) || limits == nil {
		return "", nil
	}
	usage := map[string]any{"sessionId": sessionID, "capturedAt": now.UTC().Format(isoSeconds)}
	for _, window := range usageWindows {
		if value := usageWindow(limits[window.Input], now); value != nil {
			usage[window.Key] = value
		}
	}
	if len(usage) == 2 {
		return "", nil
	}
	stateDir, err := EnsureStateDir()
	if err != nil {
		return "", err
	}
	path := UsagePath(stateDir, sessionID)
	var previous map[string]any
	if _, err := readJSON(path, &previous); err != nil {
		// The file is replaced below, which repairs it; the damage itself is worth a log line.
		logOrStderr(fmt.Sprintf("status line: %v, writing it again", err))
		previous = nil
	}
	if previous != nil && sameWindows(previous, usage) {
		return "", nil
	}
	if err := writeJSON(path, usage); err != nil {
		return "", err
	}
	return path, nil
}

// usageWindow is {usedPercentage, resetsAt} of one rate limit window, nil when the window is
// absent or out of the range Kaya accepts: a percentage from 0 to 100 and a reset within
// windowSpan of now. usedPercentage keeps the digits Claude Code sent.
func usageWindow(raw any, now time.Time) map[string]any {
	window := asObject(raw)
	if window == nil {
		return nil
	}
	used, usedOK := window["used_percentage"].(json.Number)
	resets, resetsOK := window["resets_at"].(json.Number)
	if !usedOK || !resetsOK {
		return nil
	}
	percent, err := used.Float64()
	if err != nil || percent < 0 || percent > 100 {
		return nil
	}
	resetsAt, err := resets.Float64()
	if err != nil || math.Abs(resetsAt-float64(now.Unix())) > windowSpan.Seconds() {
		return nil
	}
	return map[string]any{"usedPercentage": used, "resetsAt": int64(resetsAt)}
}

// sameWindows reports whether the windows of two usage files hold the same numbers; 23 and 23.0
// are the same percentage.
func sameWindows(a, b map[string]any) bool {
	for _, window := range usageWindows {
		if !sameWindow(asObject(a[window.Key]), asObject(b[window.Key])) {
			return false
		}
	}
	return true
}

func sameWindow(a, b map[string]any) bool {
	if a == nil || b == nil {
		return a == nil && b == nil
	}
	if len(a) != len(b) {
		return false
	}
	for key, value := range a {
		x, xOK := numberOf(value)
		y, yOK := numberOf(b[key])
		if !xOK || !yOK || x != y {
			return false
		}
	}
	return true
}

// numberOf is the value of a number decoded from JSON or built by usageWindow.
func numberOf(value any) (float64, bool) {
	switch number := value.(type) {
	case json.Number:
		f, err := number.Float64()
		return f, err == nil
	case int64:
		return float64(number), true
	}
	return 0, false
}

// loggedOnceFile remembers, per message key, the session a status line message was last
// logged for.
const loggedOnceFile = "statusline-logged.json"

// LogOnce appends message to recorder.log once per session for key. Claude Code runs the status
// line after every assistant message, so a lasting misconfiguration would otherwise add one line
// per message. Without a session id the message is logged every time. The error is the one of
// the log line itself.
func LogOnce(sessionID, key, message string) error {
	if sessionID != "" {
		stateDir, err := EnsureStateDir()
		if err != nil {
			return errors.Join(err, Log(message))
		}
		path := filepath.Join(stateDir, loggedOnceFile)
		logged := map[string]any{}
		if _, err := readJSON(path, &logged); err != nil {
			logOrStderr(fmt.Sprintf("status line: %v, starting over", err))
			logged = map[string]any{}
		}
		if logged == nil {
			logged = map[string]any{}
		}
		if logged[key] == sessionID {
			return nil
		}
		logged[key] = sessionID
		if err := writeJSON(path, logged); err != nil {
			logOrStderr(fmt.Sprintf("status line: %v; the next run logs again", err))
		}
	}
	return Log(message)
}
