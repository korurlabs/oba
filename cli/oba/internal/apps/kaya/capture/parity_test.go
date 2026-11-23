package capture

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"testing"
	"time"
)

// pinnedEventKeys are the keys of a recorded event that the server and its stored sessions rely on
// to tell what a session did: which tool ran, whether it succeeded, which agent it spawned and what
// it handed over. A key the golden events do not carry would let a change of the capture drop it
// silently.
var pinnedEventKeys = []string{
	"seq", "at", "event", "agent_id", "agent_type", "tool_name", "command", "ok", "url",
	"subagent_type", "handoff", "spawned_agent_id", "last_assistant_message",
}

func TestEveryPinnedEventKeyAppearsInAGoldenEvent(t *testing.T) {
	present := goldenEventKeys(t)
	var missing []string
	for _, key := range pinnedEventKeys {
		if !present[key] {
			missing = append(missing, key)
		}
	}
	if len(missing) > 0 {
		t.Errorf("pinned event keys that no golden event carries: %v", missing)
	}
}

// TestGoldenEventKeysAreCollectedFromEveryCase guards the guard: the key test above passes only if
// it really read the corpus.
func TestGoldenEventKeysAreCollectedFromEveryCase(t *testing.T) {
	present := goldenEventKeys(t)
	for _, key := range []string{"seq", "at", "event", "tool_name", "command", "handoff", "last_assistant_message"} {
		if !present[key] {
			t.Errorf("key %q not collected from the golden events (%d keys collected)", key, len(present))
		}
	}
}

// goldenEventKeys are the keys of every event the Python recorder wrote for the corpus.
func goldenEventKeys(t *testing.T) map[string]bool {
	t.Helper()
	present := map[string]bool{}
	add := func(event any) {
		if m, ok := event.(map[string]any); ok {
			for key := range m {
				present[key] = true
			}
		}
	}
	entries, err := os.ReadDir(filepath.Join("testdata", "cases"))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		expected := filepath.Join("testdata", "cases", entry.Name(), "expected")
		if _, err := os.Stat(filepath.Join(expected, "events.json")); err == nil {
			var events []any
			readCorpus(t, filepath.Join(expected, "events.json"), &events)
			for _, event := range events {
				add(event)
			}
		}
		if _, err := os.Stat(filepath.Join(expected, "sessions.json")); err == nil {
			var sessions map[string][]any
			readCorpus(t, filepath.Join(expected, "sessions.json"), &sessions)
			for _, events := range sessions {
				for _, event := range events {
					add(event)
				}
			}
		}
	}
	return present
}

// TestConcurrentHooksKeepSeqGaplessAndUnique runs the hooks of many parallel tool calls of one
// session, as Claude Code does, against one queue.
func TestConcurrentHooksKeepSeqGaplessAndUnique(t *testing.T) {
	base, repo, state := scratchCheckout(t)
	const workers, perWorker = 8, 10
	var wg sync.WaitGroup
	errs := make(chan error, workers*perWorker)
	for w := range workers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := range perWorker {
				in := map[string]any{"cwd": repo, "hook_event_name": "UserPromptSubmit", "session_id": "conc-1",
					"prompt": fmt.Sprintf("worker %d prompt %d", w, i)}
				if _, err := Record(in, "", time.Unix(1_800_000_000, 0), os.Stderr); err != nil {
					errs <- err
				}
			}
		}()
	}
	wg.Wait()
	close(errs)
	for err := range errs {
		t.Errorf("Record: %v", err)
	}
	events := readEvents(t, filepath.Join(SessionDir(state, "conc-1"), EventsFile))
	if len(events) != workers*perWorker {
		t.Fatalf("%d events queued, want %d (base %s)", len(events), workers*perWorker, base)
	}
	var seqs []int
	for _, event := range events {
		n, err := event.(map[string]any)["seq"].(json.Number).Int64()
		if err != nil {
			t.Fatal(err)
		}
		seqs = append(seqs, int(n))
	}
	sort.Ints(seqs)
	for i, seq := range seqs {
		if seq != i+1 {
			t.Fatalf("seqs = %v, want 1..%d without gap or repeat", seqs, workers*perWorker)
		}
	}
}

// scratchCheckout is an isolated git checkout with one commit and an empty state.
func scratchCheckout(t testing.TB) (base, repo, state string) {
	t.Helper()
	root, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	repo = filepath.Join(root, "repo")
	claude := filepath.Join(root, "claude")
	for _, d := range []string{repo, claude, filepath.Join(root, "home")} {
		if err := os.MkdirAll(d, 0o700); err != nil {
			t.Fatal(err)
		}
	}
	isolate(t, root, claude)
	gitRun(t, repo, "init", "-q", "-b", "main")
	gitRun(t, repo, "commit", "-q", "--allow-empty", "-m", "init")
	gitRun(t, repo, "remote", "add", "origin", "git@github.com:senseylabs/village.git")
	gitRun(t, repo, "config", "user.email", "dev@example.com")
	return root, repo, filepath.Join(root, "xdg", "kaya")
}
