package hook

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

func TestPreToolUse_refusesToolsWithoutAReleaseAndCallsNoAPI(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "hook_event_name": "PreToolUse", "tool_name": "Bash", "tool_input": map[string]any{"command": "ls"}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, `"permissionDecision":"deny"`) || !strings.Contains(out, "No Kaya config on this machine for") {
		t.Fatalf("out = %s, err = %v", out, err)
	}
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Read", "tool_input": map[string]any{"file_path": "x"}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("Read: out = %q, err = %v", out, err)
	}
	if kaya.Requests.Load() != 0 {
		t.Fatalf("the hook called the API %d times", kaya.Requests.Load())
	}
}

func TestPreToolUse_refusesAnEditOfADeliveredConventionDoc(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	doc := "docs/private/conventions/backend/32-soft-delete-convention.md"
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", doc: "# Soft delete\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Edit", "tool_input": map[string]any{"file_path": filepath.Join(root, filepath.FromSlash(doc))}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, `"permissionDecision":"deny"`) || !strings.Contains(out, "oba kaya propose") {
		t.Fatalf("edit of a delivered doc: %s, %v", out, err)
	}
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "docs", "private", "projects", "notes.md")}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("edit of an undelivered doc: %q, %v", out, err)
	}
}

func TestPreToolUse_refusesAnEditOfADeliveredProjectAgentsFile(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", "projects/kagi/AGENTS.md": "# Kagi\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "projects", "kagi", "AGENTS.md")}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, `"permissionDecision":"deny"`) || !strings.Contains(out, "oba kaya propose") {
		t.Fatalf("edit of a delivered project AGENTS.md: %s, %v", out, err)
	}
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "projects", "kagi", "operator", "AGENTS.md")}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("edit of a nested AGENTS.md that stays in git: %q, %v", out, err)
	}
}

func TestSessionStart_warnsWhenStartedBelowTheCheckoutRoot(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	sub := filepath.Join(root, "projects", "kagi")
	if err := os.MkdirAll(sub, 0o750); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": sub, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")
	want := "Warning: this session started in projects/kagi, below the checkout root " + root
	if err != nil || !strings.Contains(out, want) || !strings.Contains(out, "Ask the user to restart Claude Code in "+root) {
		t.Fatalf("session below the root: %s, %v", out, err)
	}
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-2", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, `"additionalContext":"Kaya config.`) || strings.Contains(out, "Warning:") {
		t.Fatalf("session at the root: %s, %v", out, err)
	}
}

func TestSessionStart_launchedOfflineSaysKayaIsUnreachable(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	t.Setenv(delivery.EnvLaunched, "1")
	t.Setenv(delivery.EnvCheckout, root)
	t.Setenv(delivery.EnvRelease, "1")
	t.Setenv(delivery.EnvReleaseState, "CACHED")
	// The launcher puts the reason on one line (see TestOfflineReason_isOneShortNonEmptyLine).
	t.Setenv(delivery.EnvOffline, "dial tcp 127.0.0.1:1: connection refused")

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")

	want := "Kaya is unreachable; this session runs on config release 1 of 2026-10-24T10:00:00.000Z. (dial tcp 127.0.0.1:1: connection refused)"
	if err != nil || !strings.Contains(out, want) {
		t.Fatalf("launched offline session start: %s, %v", out, err)
	}

	t.Setenv(delivery.EnvOffline, "")
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-2", "source": "startup"}), "hook", "session-start")
	if err != nil || strings.Contains(out, "unreachable") {
		t.Fatalf("launched online session start: %s, %v", out, err)
	}
}

func TestSessionStart_aTrackedPathInANewerReleaseRegistersTheSessionOnTheActiveOne(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	files := map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}
	kaya.Publish(t, 1, files, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	// A branch from before Kaya delivered the team conventions still tracks them.
	team := filepath.Join(root, "docs", "private", "conventions", "team.md")
	if err := os.MkdirAll(filepath.Dir(team), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(team, []byte("ours\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	kayatest.Git(t, root, "add", "--", "docs/private/conventions/team.md")
	kayatest.Git(t, root, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "team")
	files["docs/private/conventions/team.md"] = "team\n"
	kaya.Publish(t, 2, files, "AGENTS.md")

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")

	if err != nil {
		t.Fatalf("session start: %s, %v", out, err)
	}
	for _, want := range []string{"config release 1 (", "Warning: release 2 was not placed", "git rm --cached -- docs/private/conventions/team.md"} {
		if !strings.Contains(out, want) {
			t.Fatalf("session start lacks %q: %s", want, out)
		}
	}
	if data, err := os.ReadFile(team); err != nil || string(data) != "ours\n" {
		t.Fatalf("team.md = %q, %v", data, err)
	}
	data, err := os.ReadFile(filepath.Join(root, ".kaya", "sessions", "sess-1.json"))
	if err != nil {
		t.Fatalf("the session was not registered: %v", err)
	}
	var session delivery.Session
	if err := json.Unmarshal(data, &session); err != nil {
		t.Fatal(err)
	}
	if session.Release != 1 || session.State == delivery.StateUnverified || session.DeliveredBy != delivery.DeliveredByHook {
		t.Fatalf("registered session = %+v", session)
	}
}
