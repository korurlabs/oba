package delivery

import (
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

func TestDelivery_syncThenHooksWorkFromLocalState(t *testing.T) {
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")

	out, err := run(t, "", "sync")
	if err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}
	requests := kaya.Requests.Load()

	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Edit", "tool_input": map[string]any{"file_path": filepath.Join(root, "AGENTS.md")}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, "oba kaya propose") {
		t.Fatalf("edit of a delivered path: %s, %v", out, err)
	}
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "src", "main.go")}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("edit of an own file: %q, %v", out, err)
	}

	t.Setenv(delivery.EnvLaunched, "1")
	t.Setenv(delivery.EnvCheckout, root)
	t.Setenv(delivery.EnvRelease, "1")
	t.Setenv(delivery.EnvReleaseState, "FRESH")
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, `"additionalContext":"Kaya config.`) || strings.Contains(out, "reloadSkills") {
		t.Fatalf("launched session start: %s, %v", out, err)
	}
	if got := kaya.PathsSince(requests); !slices.Equal(got, []string{"/kaya/delivery/lessons"}) {
		t.Fatalf("after the launcher the hooks called %v, want only the lessons call", got)
	}
	report, err := os.ReadFile(filepath.Join(os.Getenv("XDG_STATE_HOME"), "kaya", "queue", "sessions", "sess-1", "config-release.json"))
	if err != nil || !strings.Contains(string(report), `"number":1`) || !strings.Contains(string(report), `"deliveredBy":"LAUNCHER"`) {
		t.Fatalf("report = %s, %v", report, err)
	}

	// A start without the launcher syncs: a skill change switches and reloads skills.
	t.Setenv(delivery.EnvLaunched, "")
	kaya.Publish(t, 2, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v2\n"}, "AGENTS.md")
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-2", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, `"reloadSkills":true`) || !strings.Contains(out, "- .agents/skills/demo/SKILL.md") {
		t.Fatalf("hook session start: %s, %v", out, err)
	}
	report, err = os.ReadFile(filepath.Join(os.Getenv("XDG_STATE_HOME"), "kaya", "queue", "sessions", "sess-2", "config-release.json"))
	if err != nil || !strings.Contains(string(report), `"number":2`) || !strings.Contains(string(report), `"deliveredBy":"HOOK"`) {
		t.Fatalf("report = %s, %v", report, err)
	}

	// An always-on change is staged with the restart message while this session lives on.
	kaya.Publish(t, 3, map[string]string{"AGENTS.md": "rules v3\n", ".agents/skills/demo/SKILL.md": "skill v2\n"}, "AGENTS.md")
	out, err = run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-3", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, "Config release 3 changes always-on rules; restart Claude Code to use it.") {
		t.Fatalf("always-on change: %s, %v", out, err)
	}

	out, err = run(t, "", "status", "--json")
	if err != nil || !strings.Contains(out, `"state": "STALE"`) || !strings.Contains(out, `"staged": 3`) {
		t.Fatalf("status: %s, %v", out, err)
	}
}
