package hook

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

// feedbackKaya serves the plan feedback endpoints and hands everything else to the delivery fake.
type feedbackKaya struct {
	delivery http.Handler
	mu       sync.Mutex
	pending  []client.PlanFeedback
	queries  []string
	acks     []map[string]any
	block    bool
}

func (k *feedbackKaya) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path == "/kaya/delivery/lessons" {
		w.Header().Set("ETag", `"none"`)
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": []any{}})
		return
	}
	if !strings.HasPrefix(r.URL.Path, "/kaya/delivery/plan-feedback") {
		k.delivery.ServeHTTP(w, r)
		return
	}
	if k.block {
		select {
		case <-r.Context().Done():
		case <-time.After(10 * time.Second):
		}
		return
	}
	k.mu.Lock()
	defer k.mu.Unlock()
	if r.Method == http.MethodPost {
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		k.acks = append(k.acks, body)
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": map[string]any{"acknowledged": 1}})
		return
	}
	k.queries = append(k.queries, r.URL.RawQuery)
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": k.pending})
}

// setupFeedback is a synced checkout whose hooks run as under oba kaya launch, against an API that
// also serves plan feedback, on branch feat/x.
func setupFeedback(t *testing.T) (*feedbackKaya, string) {
	t.Helper()
	kaya, root := kayatest.SetupDelivery(t)
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	fk := &feedbackKaya{delivery: kaya}
	server := httptest.NewServer(fk)
	t.Cleanup(server.Close)
	t.Setenv(config.EnvAPIURL, server.URL)
	t.Setenv(delivery.EnvLaunched, "1")
	t.Setenv(delivery.EnvCheckout, root)
	t.Setenv(delivery.EnvRelease, "1")
	t.Setenv(delivery.EnvReleaseState, "FRESH")
	previous := currentBranch
	currentBranch = func(context.Context, string) (string, error) { return "feat/x", nil }
	t.Cleanup(func() { currentBranch = previous })
	return fk, root
}

func feedbackItem(id string) client.PlanFeedback {
	return client.PlanFeedback{
		ID: id, PlanID: "p-" + id, PlanURL: "https://kaya.korur.com/plans/p-" + id, Kind: "SPEC",
		Path: "docs/claude/plans/kaya/04_X.md", VersionNumber: 2, LatestVersionNumber: 2, SessionID: "sess-1",
		ReviewerName: "Ada", Decision: client.DecisionRequestChanges, Comment: "Split step 3.",
		LineStart: 40, LineEnd: 44, Quote: "3. Build it", CreatedAt: "2026-10-25T10:00:00Z",
	}
}

func writeInbox(t *testing.T, root, sessionID string, items []client.PlanFeedback) {
	t.Helper()
	dir := filepath.Join(root, ".kaya", "inbox")
	if err := os.MkdirAll(dir, 0o750); err != nil {
		t.Fatal(err)
	}
	data, err := json.Marshal(items)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, sessionID+".json"), data, 0o600); err != nil {
		t.Fatal(err)
	}
}

func additionalContext(t *testing.T, out, event string) string {
	t.Helper()
	var answer struct {
		HookSpecificOutput struct {
			HookEventName     string `json:"hookEventName"`
			AdditionalContext string `json:"additionalContext"`
		} `json:"hookSpecificOutput"`
	}
	if err := json.Unmarshal([]byte(out), &answer); err != nil {
		t.Fatalf("hook output %q is not JSON: %v", out, err)
	}
	if answer.HookSpecificOutput.HookEventName != event {
		t.Fatalf("hookEventName = %q, want %q", answer.HookSpecificOutput.HookEventName, event)
	}
	return answer.HookSpecificOutput.AdditionalContext
}

func TestPostToolUse_addsTheInboxOnceAndQueuesItsAcks(t *testing.T) {
	fk, root := setupFeedback(t)
	writeInbox(t, root, "sess-1", []client.PlanFeedback{feedbackItem("f-1")})
	event := kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "hook_event_name": "PostToolUse", "tool_name": "Read"})

	out, err := run(t, event, "hook", "post-tool-use")
	if err != nil {
		t.Fatal(err)
	}
	text := additionalContext(t, out, "PostToolUse")
	want := "Revise the plan before you implement.\nKaya plan feedback from Ada on docs/claude/plans/kaya/04_X.md version 2: " +
		"Changes requested. Lines 40-44: \"3. Build it\". Split step 3."
	if text != want {
		t.Fatalf("context = %q", text)
	}
	acks, err := os.ReadFile(filepath.Join(root, ".kaya", "inbox", "sess-1.acks.json"))
	if err != nil || !strings.Contains(string(acks), `"f-1"`) {
		t.Fatalf("acks = %s, %v", acks, err)
	}
	if out, err := run(t, event, "hook", "post-tool-use"); err != nil || out != "" {
		t.Fatalf("second hook: %q, %v", out, err)
	}
	if len(fk.queries)+len(fk.acks) != 0 {
		t.Fatal("the hook called the API")
	}
}

func TestUserPromptSubmit_withoutAnInboxPrintsNothing(t *testing.T) {
	_, root := setupFeedback(t)
	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "hook_event_name": "UserPromptSubmit", "prompt": "go"}), "hook", "user-prompt-submit")
	if err != nil || out != "" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestUserPromptSubmit_cutsAtMaxContextAndKeepsTheRest(t *testing.T) {
	_, root := setupFeedback(t)
	var items []client.PlanFeedback
	for _, id := range []string{"f-1", "f-2", "f-3", "f-4", "f-5"} {
		item := feedbackItem(id)
		item.Decision = client.DecisionComment
		item.Comment = strings.Repeat("x", 4000)
		items = append(items, item)
	}
	writeInbox(t, root, "sess-1", items)
	event := kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "hook_event_name": "UserPromptSubmit", "prompt": "go"})

	out, err := run(t, event, "hook", "user-prompt-submit")
	if err != nil {
		t.Fatal(err)
	}
	text := additionalContext(t, out, "UserPromptSubmit")
	if len(text) > maxContext || !strings.HasSuffix(text, "\nand 3 more on the Kaya plan page https://kaya.korur.com/plans/p-f-3") ||
		strings.Contains(text, "Revise the plan") || strings.Count(text, "Kaya plan feedback from") != 2 {
		t.Fatalf("context (%d characters) ends %q", len(text), text[max(0, len(text)-120):])
	}
	out, err = run(t, event, "hook", "user-prompt-submit")
	if err != nil || strings.Count(additionalContext(t, out, "UserPromptSubmit"), "Kaya plan feedback from") != 2 {
		t.Fatalf("second hook: %s, %v", out, err)
	}
}

func TestSessionStart_addsTheFeedbackOfTheBranchAndAcksIt(t *testing.T) {
	fk, root := setupFeedback(t)
	item := feedbackItem("f-1")
	item.Kind, item.Path, item.SessionID, item.Decision, item.LineStart, item.LineEnd, item.Quote = "SESSION", "", "sess-old", client.DecisionApprove, 0, 0, ""
	item.Comment = ""
	fk.pending = []client.PlanFeedback{item}

	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-9", "source": "startup"}), "hook", "session-start")
	if err != nil {
		t.Fatal(err)
	}
	text := additionalContext(t, out, "SessionStart")
	if !strings.HasPrefix(text, "Kaya config. ") || !strings.HasSuffix(text, "\nKaya plan feedback from Ada on the plan of session sess-old version 2: Approved.") {
		t.Fatalf("context = %q", text)
	}
	if len(fk.queries) != 1 || fk.queries[0] != "branch=feat%2Fx&remoteUrl=git%40github.com%3Aexample%2Fdemo.git" {
		t.Fatalf("queries = %v", fk.queries)
	}
	if len(fk.acks) != 1 || fk.acks[0]["sessionId"] != "sess-9" {
		t.Fatalf("acks = %v", fk.acks)
	}
}

func TestSessionStart_unreachableFeedbackAddsNothingWithinItsBound(t *testing.T) {
	fk, root := setupFeedback(t)
	fk.block = true
	fk.pending = []client.PlanFeedback{feedbackItem("f-1")}

	started := time.Now()
	out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-9", "source": "startup"}), "hook", "session-start")
	elapsed := time.Since(started)

	if err != nil || !strings.Contains(out, `"additionalContext":"Kaya config.`) || strings.Contains(out, "Kaya plan feedback") {
		t.Fatalf("out = %s, err = %v", out, err)
	}
	if elapsed > feedbackTimeout+time.Second {
		t.Fatalf("the session start took %v", elapsed)
	}
	if _, err := os.Stat(filepath.Join(root, ".kaya", "inbox", "sess-9.acks.json")); !os.IsNotExist(err) {
		t.Fatalf("acks were queued for feedback the session never got: %v", err)
	}
}

func TestSessionEnd_removesTheInbox(t *testing.T) {
	_, root := setupFeedback(t)
	writeInbox(t, root, "sess-1", []client.PlanFeedback{feedbackItem("f-1")})
	if out, err := run(t, kayatest.HookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "reason": "clear"}), "hook", "session-end"); err != nil {
		t.Fatalf("%s, %v", out, err)
	}
	if _, err := os.Stat(filepath.Join(root, ".kaya", "inbox", "sess-1.json")); !os.IsNotExist(err) {
		t.Fatalf("the inbox was kept: %v", err)
	}
}

func TestFeedbackContext_cutsASingleLongItemOnlyWithEnoughRoom(t *testing.T) {
	item := feedbackItem("f-1")
	item.Comment = strings.Repeat("é", 3000)
	if text, given := feedbackContext([]client.PlanFeedback{item}, "sess-1", 2000); given != 1 || len(text) > 2000 || !strings.HasPrefix(text, "Revise the plan") {
		t.Fatalf("given = %d, %d bytes", given, len(text))
	}
	if text, given := feedbackContext([]client.PlanFeedback{item}, "sess-1", minFeedbackContext-1); given != 0 || text != "" {
		t.Fatalf("given = %d, text = %q", given, text)
	}
}
