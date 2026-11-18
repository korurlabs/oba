package cmd

import (
	"bytes"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/kaya-cli/internal/capture"
)

// recordEnv isolates HOME, the state directory and git, and returns a checkout with a committed
// kaya.yaml (when joined) and the state directory.
func recordEnv(t *testing.T, joined bool) (repo, state string) {
	t.Helper()
	base, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	repo = filepath.Join(base, "repo")
	if err := os.MkdirAll(repo, 0o700); err != nil {
		t.Fatal(err)
	}
	for key, value := range map[string]string{
		"HOME": filepath.Join(base, "home"), "XDG_STATE_HOME": filepath.Join(base, "xdg"),
		"CLAUDE_CONFIG_DIR": filepath.Join(base, "claude"), "GIT_CONFIG_GLOBAL": os.DevNull, "GIT_CONFIG_NOSYSTEM": "1",
		"GIT_AUTHOR_NAME": "g", "GIT_AUTHOR_EMAIL": "g@example.com", "GIT_COMMITTER_NAME": "g", "GIT_COMMITTER_EMAIL": "g@example.com",
		"KAYA_SENDER": "off", "CLAUDE_PROJECT_DIR": "", "KAYA_RECORDER_TOKEN": "",
	} {
		t.Setenv(key, value)
	}
	gitIn(t, repo, "init", "-q", "-b", "main")
	if joined {
		if err := os.WriteFile(filepath.Join(repo, "kaya.yaml"), []byte("# joined Kaya\n"), 0o600); err != nil {
			t.Fatal(err)
		}
		gitIn(t, repo, "add", "kaya.yaml")
	}
	gitIn(t, repo, "commit", "-q", "--allow-empty", "-m", "init")
	return repo, filepath.Join(base, "xdg", "kaya")
}

func gitIn(t *testing.T, dir string, args ...string) {
	t.Helper()
	if out, err := exec.Command("git", append([]string{"-C", dir}, args...)...).CombinedOutput(); err != nil {
		t.Fatalf("git %v: %v\n%s", args, err, out)
	}
}

// runRecord is `kaya hook record` with the event on stdin; stdout and stderr stay apart.
func runRecord(t *testing.T, stdin string) (stdout, stderr string, err error) {
	t.Helper()
	var out, errOut bytes.Buffer
	rootCmd.SetArgs([]string{"hook", "record"})
	rootCmd.SetIn(strings.NewReader(stdin))
	rootCmd.SetOut(&out)
	rootCmd.SetErr(&errOut)
	_, err = rootCmd.ExecuteC()
	return out.String(), errOut.String(), err
}

func oneLine(text string) bool {
	return strings.Count(strings.TrimRight(text, "\n"), "\n") == 0 && strings.TrimSpace(text) != ""
}

func TestRecordHook_printsNothingOnStdoutForEveryEvent(t *testing.T) {
	repo, state := recordEnv(t, true)
	events := []string{"SessionStart", "UserPromptSubmit", "PreToolUse", "PostToolUse", "PostToolUseFailure", "SubagentStart",
		"SubagentStop", "Stop", "StopFailure", "Notification", "PreCompact", "SessionEnd", "SomeFutureEvent"}
	for _, event := range events {
		stdout, stderr, err := runRecord(t, hookEvent(t, map[string]any{"cwd": repo, "hook_event_name": event, "session_id": "rec-1",
			"tool_name": "Bash", "tool_input": map[string]any{"command": "ls"}, "prompt": "hello"}))
		if err != nil || stdout != "" || stderr != "" {
			t.Errorf("%s: err = %v, stdout = %q, stderr = %q; want a silent success", event, err, stdout, stderr)
		}
	}
	data, err := os.ReadFile(filepath.Join(capture.SessionDir(state, "rec-1"), capture.EventsFile))
	if err != nil {
		t.Fatal(err)
	}
	if got := strings.Count(string(data), "\n"); got != len(events) {
		t.Errorf("%d events queued, want %d", got, len(events))
	}
}

func TestRecordHook_failuresAreOneStderrLineAndExitZero(t *testing.T) {
	repo, _ := recordEnv(t, true)
	outside := t.TempDir()
	cases := map[string]string{
		"malformed JSON":           `{"hook_event_name": `,
		"not an object":            `[1,2]`,
		"empty input":              ``,
		"no session id":            hookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop"}),
		"an invalid session id":    hookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop", "session_id": "ab/../c d"}),
		"a directory outside git":  hookEvent(t, map[string]any{"cwd": outside, "hook_event_name": "Stop", "session_id": "rec-2"}),
		"a directory that is gone": hookEvent(t, map[string]any{"cwd": filepath.Join(outside, "gone"), "hook_event_name": "Stop", "session_id": "rec-2"}),
	}
	for name, stdin := range cases {
		t.Run(name, func(t *testing.T) {
			stdout, stderr, err := runRecord(t, stdin)
			if err != nil || stdout != "" {
				t.Errorf("err = %v, stdout = %q; want exit 0 and an empty stdout", err, stdout)
			}
			if !oneLine(stderr) || !strings.HasPrefix(stderr, "kaya hook: nothing recorded") {
				t.Errorf("stderr = %q, want exactly one line starting with %q", stderr, "kaya hook: nothing recorded")
			}
		})
	}
}

func TestRecordHook_anUnwritableQueueIsOneStderrLine(t *testing.T) {
	repo, _ := recordEnv(t, true)
	// The state directory's parent is a regular file, so nothing can be created below it.
	blocker := filepath.Join(t.TempDir(), "blocker")
	if err := os.WriteFile(blocker, []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("XDG_STATE_HOME", blocker)
	stdout, stderr, err := runRecord(t, hookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop", "session_id": "rec-3"}))
	if err != nil || stdout != "" || !oneLine(stderr) || !strings.HasPrefix(stderr, "kaya hook: nothing recorded") {
		t.Errorf("err = %v, stdout = %q, stderr = %q; want exit 0 and one stderr line", err, stdout, stderr)
	}
}

func TestRecordHook_aCheckoutWithoutKayaYAMLWritesNothing(t *testing.T) {
	repo, state := recordEnv(t, false)
	stdout, stderr, err := runRecord(t, hookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "SessionStart", "session_id": "rec-4"}))
	if err != nil || stdout != "" || stderr != "" {
		t.Errorf("err = %v, stdout = %q, stderr = %q; want silence", err, stdout, stderr)
	}
	if _, err := os.Stat(capture.SessionDir(state, "rec-4")); !os.IsNotExist(err) {
		t.Errorf("a session directory was written for a checkout without kaya.yaml: %v", err)
	}
	if _, err := os.Stat(filepath.Join(state, "sender.started")); !os.IsNotExist(err) {
		t.Errorf("a sender was started for a checkout without kaya.yaml: %v", err)
	}
}

func TestRecordHook_senderOffStartsNoSender(t *testing.T) {
	repo, state := recordEnv(t, true)
	if _, stderr, err := runRecord(t, hookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "SessionStart", "session_id": "rec-5"})); err != nil || stderr != "" {
		t.Fatalf("err = %v, stderr = %q", err, stderr)
	}
	for _, name := range []string{"sender.started", "sender.lock"} {
		if _, err := os.Stat(filepath.Join(state, name)); !os.IsNotExist(err) {
			t.Errorf("%s exists with KAYA_SENDER=off: %v", name, err)
		}
	}
}
