package cmd

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/capture"
	"github.com/korurlabs/kaya-cli/internal/install"
)

const (
	// statusLineNested marks a status line command run by kaya statusline. Such a run neither
	// captures nor wraps, so a developer's command that calls kaya statusline cannot recurse.
	statusLineNested = "KAYA_STATUSLINE"
	// wrappedTimeout bounds the developer's own status line command. Claude Code cancels a
	// status line that a newer message overtakes, so a slow command would never show.
	wrappedTimeout = 2 * time.Second
	// wrappedKillWait bounds the wait for the pipes after the command's process group was
	// killed, for a process that left the group and still holds them.
	wrappedKillWait = time.Second
	// stderrLimit is how much of the wrapped command's stderr reaches the log.
	stderrLimit = 2000
	// maxStatusLineInput bounds the session JSON the status line reads.
	maxStatusLineInput = 1 << 20
)

var statusLineCmd = &cobra.Command{
	Use:   "statusline",
	Short: "Claude Code status line: captures the usage limits for Kaya and prints your own status line",
	Long: "Reads the session JSON Claude Code passes to its status line command. When rate_limits holds a " +
		"five_hour or seven_day window, writes it to the queue's usage file, which kaya sender adds to the " +
		"session's next batch. Then prints the output of your own statusLine command from the Claude Code " +
		"user settings, run with the same input and " + statusLineNested + "=1 for at most 2 seconds, or " +
		"the default line `<model> | 5h 23% | 7d 41%`. Registered by the project settings. A failure is " +
		"logged to recorder.log of the state directory and never fails the line.",
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		raw, err := io.ReadAll(io.LimitReader(cmd.InOrStdin(), maxStatusLineInput))
		if err != nil {
			statusLog(cmd.ErrOrStderr(), fmt.Sprintf("status line: stdin unreadable: %v", err))
		}
		statusLine(cmd.Context(), raw, time.Now(), cmd.OutOrStdout(), cmd.ErrOrStderr())
		return nil
	},
}

func init() {
	rootCmd.AddCommand(statusLineCmd)
}

// statusLine prints one status line for the session JSON raw and captures its usage limits.
// Every failure is logged and ends in the default line, so the developer always sees a line.
func statusLine(ctx context.Context, raw []byte, now time.Time, stdout, stderr io.Writer) {
	data := map[string]any{}
	if len(bytes.TrimSpace(raw)) > 0 {
		in, err := capture.Decode(raw)
		if err != nil {
			statusLog(stderr, fmt.Sprintf("status line: %v", err))
		} else {
			data = in
		}
	}
	sessionID, _ := data["session_id"].(string)
	nested := os.Getenv(statusLineNested) == "1"
	if !nested {
		if _, err := capture.CaptureUsage(data, now); err != nil {
			statusLog(stderr, fmt.Sprintf("status line: usage not captured: %v", err))
		}
	}
	command := ""
	if !nested {
		command = wrappedCommand(sessionID, stderr)
	}
	if command == "" {
		_, _ = fmt.Fprintln(stdout, defaultStatusLine(data, ""))
		return
	}
	out, failure := runWrapped(ctx, command, raw, stderr)
	if failure == "" {
		_, _ = stdout.Write(out)
		return
	}
	statusLog(stderr, fmt.Sprintf("status line: your command %q failed, %s", command, failure))
	note := "(your status line failed, see the kaya recorder.log)"
	if stateDir, err := capture.EnsureStateDir(); err == nil {
		note = fmt.Sprintf("(your status line failed, see %s)", filepath.Join(stateDir, capture.LogFile))
	}
	_, _ = fmt.Fprintln(stdout, defaultStatusLine(data, note))
}

// defaultStatusLine is `<model> | 5h 23% | 7d 41%`, with note appended when it is given.
// Percentages round half to even, as the line has always shown them.
func defaultStatusLine(data map[string]any, note string) string {
	parts := []string{"Claude"}
	if model, ok := data["model"].(map[string]any); ok {
		for _, key := range []string{"display_name", "id"} {
			if name, ok := model[key].(string); ok && name != "" {
				parts[0] = name
				break
			}
		}
	}
	limits, _ := data["rate_limits"].(map[string]any)
	for _, window := range []struct{ Key, Label string }{{"five_hour", "5h"}, {"seven_day", "7d"}} {
		value, _ := limits[window.Key].(map[string]any)
		used, ok := value["used_percentage"].(json.Number)
		if !ok {
			continue
		}
		percent, err := used.Float64()
		if err != nil {
			continue
		}
		parts = append(parts, fmt.Sprintf("%s %d%%", window.Label, int64(math.RoundToEven(percent))))
	}
	line := strings.Join(parts, " | ")
	if note != "" {
		line += " " + note
	}
	return line
}

// wrappedCommand is the developer's own status line command from the Claude Code user settings,
// "" when there is none or when it runs kaya statusline itself.
func wrappedCommand(sessionID string, stderr io.Writer) string {
	home, err := os.UserHomeDir()
	if err != nil {
		logOnce(stderr, sessionID, "noHome", fmt.Sprintf("status line: no home directory, your status line is not shown: %v", err))
		return ""
	}
	path := install.ClaudeSettingsPath(install.Options{Home: home, ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR")})
	data, err := os.ReadFile(path) // #nosec G304 -- the Claude Code user settings
	if errors.Is(err, os.ErrNotExist) {
		return ""
	}
	var settings map[string]any
	if err == nil {
		err = json.Unmarshal(data, &settings)
	}
	if err != nil {
		logOnce(stderr, sessionID, "settingsUnreadable "+path,
			fmt.Sprintf("status line: %s unreadable, your status line is not shown: %v", path, err))
		return ""
	}
	entry, _ := settings["statusLine"].(map[string]any)
	command, _ := entry["command"].(string)
	if strings.TrimSpace(command) == "" {
		return ""
	}
	if runsKayaStatusLine(command) {
		logOnce(stderr, sessionID, "selfReference "+command,
			fmt.Sprintf("status line: your command %q runs kaya statusline itself, so it is not wrapped and the default line is shown", command))
		return ""
	}
	return command
}

// runsKayaStatusLine reports whether command calls this kaya binary's statusline: one word,
// with ~ and variables expanded and links resolved, is this executable, and another is
// statusline. KAYA_STATUSLINE already stops a recursion; this spares the extra process.
func runsKayaStatusLine(command string) bool {
	self, err := os.Executable()
	if err != nil {
		return false
	}
	self, err = filepath.EvalSymlinks(self)
	if err != nil {
		return false
	}
	words := strings.Fields(command)
	hasStatusLine, hasSelf := false, false
	for _, word := range words {
		word = strings.Trim(word, `"'`)
		if word == "statusline" {
			hasStatusLine = true
			continue
		}
		if resolveWord(word) == self {
			hasSelf = true
		}
	}
	return hasStatusLine && hasSelf
}

// resolveWord is the real path of the program a command word names, "" when it names none.
func resolveWord(word string) string {
	word = os.ExpandEnv(word)
	if word == "~" || strings.HasPrefix(word, "~/") {
		home, err := os.UserHomeDir()
		if err != nil {
			return ""
		}
		word = filepath.Join(home, strings.TrimPrefix(word, "~"))
	}
	if !strings.Contains(word, "/") {
		found, err := exec.LookPath(word)
		if err != nil {
			return ""
		}
		word = found
	}
	resolved, err := filepath.EvalSymlinks(word)
	if err != nil {
		return ""
	}
	return resolved
}

// runWrapped runs the developer's command through sh with the session JSON on stdin and
// returns its stdout, or a failure reason when it failed or timed out. It runs in its own
// process group, so a timeout also ends the processes it started.
func runWrapped(ctx context.Context, command string, raw []byte, stderr io.Writer) ([]byte, string) {
	ctx, cancel := context.WithTimeout(ctx, wrappedTimeout)
	defer cancel()
	run := exec.CommandContext(ctx, "/bin/sh", "-c", command) // #nosec G204 -- the developer's own status line command from their Claude Code settings
	run.Stdin = bytes.NewReader(raw)
	run.Env = append(os.Environ(), statusLineNested+"=1")
	run.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	run.Cancel = func() error {
		return syscall.Kill(-run.Process.Pid, syscall.SIGKILL)
	}
	run.WaitDelay = wrappedKillWait
	var out, errOut bytes.Buffer
	run.Stdout = &out
	run.Stderr = &errOut
	err := run.Run()
	if err != nil && ctx.Err() != nil {
		return nil, fmt.Sprintf("timed out after %s", wrappedTimeout)
	}
	if err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return nil, fmt.Sprintf("exited %d: %s", exitErr.ExitCode(), cutText(errOut.String(), stderrLimit))
		}
		return nil, fmt.Sprintf("could not run: %v", err)
	}
	if errOut.Len() > 0 {
		statusLog(stderr, "status line: your command wrote to stderr: "+cutText(errOut.String(), stderrLimit))
	}
	return out.Bytes(), ""
}

// cutText keeps the first limit characters of s.
func cutText(s string, limit int) string {
	runes := []rune(strings.ToValidUTF8(s, "�"))
	if len(runes) <= limit {
		return string(runes)
	}
	return string(runes[:limit])
}

// statusLog writes one line to recorder.log, or to stderr when the log cannot be written, so
// the reason is never lost.
func statusLog(stderr io.Writer, message string) {
	if err := capture.Log(message); err != nil {
		_, _ = fmt.Fprintf(stderr, "kaya status line: %v: %s\n", err, message)
	}
}

// logOnce is statusLog once per session and key.
func logOnce(stderr io.Writer, sessionID, key, message string) {
	if err := capture.LogOnce(sessionID, key, message); err != nil {
		_, _ = fmt.Fprintf(stderr, "kaya status line: %v: %s\n", err, message)
	}
}
