package hook

import (
	"bytes"
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// listedRemote is on the organization's repository list in every record test.
const listedRemote = "github.com/example/app"

// recordEnv isolates HOME, the config and state directories and git, caches a repository list
// that holds listedRemote, and returns a checkout whose origin is on it (when joined) or another
// repository's, and the state directory.
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
		"HOME": filepath.Join(base, "home"), "XDG_STATE_HOME": filepath.Join(base, "xdg"), "XDG_CONFIG_HOME": filepath.Join(base, "config"),
		"CLAUDE_CONFIG_DIR": filepath.Join(base, "claude"), "GIT_CONFIG_GLOBAL": os.DevNull, "GIT_CONFIG_NOSYSTEM": "1",
		"GIT_AUTHOR_NAME": "g", "GIT_AUTHOR_EMAIL": "g@example.com", "GIT_COMMITTER_NAME": "g", "GIT_COMMITTER_EMAIL": "g@example.com",
		"KAYA_SENDER": "off", "CLAUDE_PROJECT_DIR": "", "KAYA_RECORDER_TOKEN": "",
	} {
		t.Setenv(key, value)
	}
	gitIn(t, repo, "init", "-q", "-b", "main")
	remote := "git@github.com:someone/private.git"
	if joined {
		remote = "git@github.com:example/app.git"
	}
	gitIn(t, repo, "remote", "add", "origin", remote)
	gitIn(t, repo, "commit", "-q", "--allow-empty", "-m", "init")
	kayatest.ListRepositories(t, "org-1", listedRemote)
	return repo, filepath.Join(base, "xdg", "kaya")
}

func gitIn(t *testing.T, dir string, args ...string) {
	t.Helper()
	if out, err := exec.Command("git", append([]string{"-C", dir}, args...)...).CombinedOutput(); err != nil {
		t.Fatalf("git %v: %v\n%s", args, err, out)
	}
}

// runRecord is `oba kaya hook record` with the event on stdin; stdout and stderr stay apart.
func runRecord(t *testing.T, stdin string) (stdout, stderr string, err error) {
	t.Helper()
	var out, errOut bytes.Buffer
	err = Run(context.Background(), "record", strings.NewReader(stdin), &out, &errOut)
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
		stdout, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": event, "session_id": "rec-1",
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
		"no session id":            kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop"}),
		"an invalid session id":    kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop", "session_id": "ab/../c d"}),
		"a directory outside git":  kayatest.HookEvent(t, map[string]any{"cwd": outside, "hook_event_name": "Stop", "session_id": "rec-2"}),
		"a directory that is gone": kayatest.HookEvent(t, map[string]any{"cwd": filepath.Join(outside, "gone"), "hook_event_name": "Stop", "session_id": "rec-2"}),
	}
	for name, stdin := range cases {
		t.Run(name, func(t *testing.T) {
			stdout, stderr, err := runRecord(t, stdin)
			if err != nil || stdout != "" {
				t.Errorf("err = %v, stdout = %q; want exit 0 and an empty stdout", err, stdout)
			}
			if !oneLine(stderr) || !strings.HasPrefix(stderr, "oba kaya hook: nothing recorded") {
				t.Errorf("stderr = %q, want exactly one line starting with %q", stderr, "oba kaya hook: nothing recorded")
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
	stdout, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop", "session_id": "rec-3"}))
	if err != nil || stdout != "" || !oneLine(stderr) || !strings.HasPrefix(stderr, "oba kaya hook: nothing recorded") {
		t.Errorf("err = %v, stdout = %q, stderr = %q; want exit 0 and one stderr line", err, stdout, stderr)
	}
}

// assertNothingRecorded fails when the session or a sender left a trace in the state directory.
func assertNothingRecorded(t *testing.T, state, session string) {
	t.Helper()
	if _, err := os.Stat(capture.SessionDir(state, session)); !os.IsNotExist(err) {
		t.Errorf("a session directory was written for an unrecognised checkout: %v", err)
	}
	if _, err := os.Stat(filepath.Join(state, "sender.started")); !os.IsNotExist(err) {
		t.Errorf("a sender was started for an unrecognised checkout: %v", err)
	}
}

func TestRecordHook_aCheckoutWithoutAListedRemoteWritesNothing(t *testing.T) {
	repo, state := recordEnv(t, false)
	// A committed kaya.yaml no longer makes a checkout Kaya's.
	if err := os.WriteFile(filepath.Join(repo, "kaya.yaml"), []byte("remoteUrl: git@github.com:example/app.git\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	gitIn(t, repo, "add", "kaya.yaml")
	gitIn(t, repo, "commit", "-q", "-m", "config")
	stdout, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "SessionStart", "session_id": "rec-4"}))
	if err != nil || stdout != "" || stderr != "" {
		t.Errorf("err = %v, stdout = %q, stderr = %q; want silence", err, stdout, stderr)
	}
	assertNothingRecorded(t, state, "rec-4")
}

func TestRecordHook_withoutACachedListNothingIsRecorded(t *testing.T) {
	repo, state := recordEnv(t, true)
	path, err := delivery.RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	stdout, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "SessionStart", "session_id": "rec-6"}))
	if err != nil || stdout != "" || stderr != "" {
		t.Errorf("err = %v, stdout = %q, stderr = %q; want silence", err, stdout, stderr)
	}
	assertNothingRecorded(t, state, "rec-6")
}

func TestRecordHook_aSessionAnywhereInARecognisedCheckoutIsRecorded(t *testing.T) {
	repo, state := recordEnv(t, true)
	sub := filepath.Join(repo, "src", "app")
	if err := os.MkdirAll(sub, 0o700); err != nil {
		t.Fatal(err)
	}
	stdout, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": sub, "hook_event_name": "SessionStart", "session_id": "rec-7"}))
	if err != nil || stdout != "" || stderr != "" {
		t.Fatalf("err = %v, stdout = %q, stderr = %q; want a silent success", err, stdout, stderr)
	}
	if _, err := os.Stat(filepath.Join(capture.SessionDir(state, "rec-7"), capture.EventsFile)); err != nil {
		t.Fatalf("the event was not queued: %v", err)
	}
}

func TestRecordHook_aDamagedListIsOneStderrLine(t *testing.T) {
	repo, state := recordEnv(t, true)
	path, err := delivery.RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	stdout, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "Stop", "session_id": "rec-8"}))
	if err != nil || stdout != "" || !oneLine(stderr) || !strings.Contains(stderr, path) {
		t.Errorf("err = %v, stdout = %q, stderr = %q; want exit 0 and one line naming the list", err, stdout, stderr)
	}
	assertNothingRecorded(t, state, "rec-8")
}

func TestRecordHook_senderOffStartsNoSender(t *testing.T) {
	repo, state := recordEnv(t, true)
	if _, stderr, err := runRecord(t, kayatest.HookEvent(t, map[string]any{"cwd": repo, "hook_event_name": "SessionStart", "session_id": "rec-5"})); err != nil || stderr != "" {
		t.Fatalf("err = %v, stderr = %q", err, stderr)
	}
	for _, name := range []string{"sender.started", "sender.lock"} {
		if _, err := os.Stat(filepath.Join(state, name)); !os.IsNotExist(err) {
			t.Errorf("%s exists with KAYA_SENDER=off: %v", name, err)
		}
	}
}
