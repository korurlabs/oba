package statusline

import (
	"bytes"
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
)

const statusLineInput = `{"session_id":"s-1","model":{"display_name":"Opus"},` +
	`"rate_limits":{"five_hour":{"used_percentage":22.5,"resets_at":1800003600},` +
	`"seven_day":{"used_percentage":41,"resets_at":1800003600}}}`

// statusLineEnv isolates the state directory and the Claude Code user settings, with the given
// statusLine command ("" for none).
func statusLineEnv(t *testing.T, command string) string {
	t.Helper()
	state := t.TempDir()
	config := t.TempDir()
	t.Setenv("XDG_STATE_HOME", state)
	t.Setenv("CLAUDE_CONFIG_DIR", config)
	t.Setenv(Nested, "")
	if command != "" {
		settings := `{"statusLine":{"type":"command","command":` + quoteJSON(command) + `}}`
		if err := os.WriteFile(filepath.Join(config, "settings.json"), []byte(settings), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return filepath.Join(state, "kaya")
}

func quoteJSON(s string) string {
	return `"` + strings.ReplaceAll(strings.ReplaceAll(s, `\`, `\\`), `"`, `\"`) + `"`
}

func runStatusLine(t *testing.T) (string, string) {
	t.Helper()
	var out, errOut bytes.Buffer
	statusLine(context.Background(), []byte(statusLineInput), time.Unix(1_800_000_000, 0), &out, &errOut)
	return out.String(), errOut.String()
}

func TestStatusLinePrintsDefaultLineAndCapturesUsage(t *testing.T) {
	stateDir := statusLineEnv(t, "")
	out, errOut := runStatusLine(t)
	if out != "Opus | 5h 22% | 7d 41%\n" || errOut != "" {
		t.Fatalf("stdout %q, stderr %q", out, errOut)
	}
	if _, err := os.Stat(capture.UsagePath(stateDir, "s-1")); err != nil {
		t.Fatalf("usage file: %v", err)
	}
}

func TestStatusLinePrintsWrappedOutputUnchanged(t *testing.T) {
	statusLineEnv(t, `printf 'mine %s:' "$KAYA_STATUSLINE"; cat`)
	out, _ := runStatusLine(t)
	if out != "mine 1:"+statusLineInput {
		t.Fatalf("stdout %q", out)
	}
}

func TestStatusLineFallsBackWhenWrappedCommandFails(t *testing.T) {
	stateDir := statusLineEnv(t, "echo broken >&2; exit 3")
	out, _ := runStatusLine(t)
	want := "Opus | 5h 22% | 7d 41% (your status line failed, see " + filepath.Join(stateDir, capture.LogFile) + ")\n"
	if out != want {
		t.Fatalf("stdout %q, want %q", out, want)
	}
	log, err := os.ReadFile(filepath.Join(stateDir, capture.LogFile))
	if err != nil || !strings.Contains(string(log), "exited 3: broken") {
		t.Fatalf("recorder.log %q, err %v", log, err)
	}
}

func TestStatusLineNestedRunNeitherCapturesNorWraps(t *testing.T) {
	stateDir := statusLineEnv(t, "echo wrapped")
	t.Setenv(Nested, "1")
	out, _ := runStatusLine(t)
	if out != "Opus | 5h 22% | 7d 41%\n" {
		t.Fatalf("stdout %q", out)
	}
	if _, err := os.Stat(capture.UsagePath(stateDir, "s-1")); !os.IsNotExist(err) {
		t.Fatalf("a nested run wrote the usage file: %v", err)
	}
}
