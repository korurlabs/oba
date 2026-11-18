package cmd

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
)

// fakeKaya is the delivery API of one repository, in the CustomResponse envelope.
type fakeKaya struct {
	priv     ed25519.PrivateKey
	requests atomic.Int64
	// paths holds the path of every request in order, so a test can name what a hook called.
	pathsMu  sync.Mutex
	paths    []string
	current  map[string]any
	releases map[string]map[string]any
	blobs    map[string][]byte
	// proposals holds the bodies POST /delivery/proposals received; propose answers them.
	proposals []map[string]any
	propose   func(body map[string]any) (int, map[string]any)
}

// pathsSince returns the paths requested after the first n requests.
func (k *fakeKaya) pathsSince(n int64) []string {
	k.pathsMu.Lock()
	defer k.pathsMu.Unlock()
	return slices.Clone(k.paths[n:])
}

func (k *fakeKaya) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	k.requests.Add(1)
	k.pathsMu.Lock()
	k.paths = append(k.paths, r.URL.Path)
	k.pathsMu.Unlock()
	if r.Header.Get("Authorization") != "Bearer kyt_test" {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	data := func(value any) {
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": value})
	}
	switch {
	case r.Method == http.MethodPost && r.URL.Path == "/kaya/delivery/proposals" && k.propose != nil:
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		k.proposals = append(k.proposals, body)
		status, payload := k.propose(body)
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(payload)
	case r.URL.Path == "/kaya/delivery/current":
		w.Header().Set("ETag", fmt.Sprintf("%q", k.current["manifestSha256"]))
		data(k.current)
	case strings.HasPrefix(r.URL.Path, "/kaya/delivery/releases/"):
		data(k.releases[strings.TrimPrefix(r.URL.Path, "/kaya/delivery/releases/")])
	case strings.HasPrefix(r.URL.Path, "/kaya/delivery/blobs/"):
		_, _ = w.Write(k.blobs[strings.TrimPrefix(r.URL.Path, "/kaya/delivery/blobs/")])
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

func hexSum(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

func (k *fakeKaya) publish(t *testing.T, number int, files map[string]string, alwaysOn ...string) {
	t.Helper()
	type entry struct {
		Path     string `json:"path"`
		SHA256   string `json:"sha256"`
		Mode     string `json:"mode"`
		AlwaysOn bool   `json:"alwaysOn"`
	}
	var entries []entry
	paths := []string{"AGENTS.md", ".agents/skills/demo/SKILL.md"}
	var extra []string
	for path := range files {
		if path != paths[0] && path != paths[1] {
			extra = append(extra, path)
		}
	}
	sort.Strings(extra)
	for _, path := range append(paths, extra...) {
		content := []byte(files[path])
		k.blobs[hexSum(content)] = content
		on := false
		for _, a := range alwaysOn {
			on = on || a == path
		}
		entries = append(entries, entry{Path: path, SHA256: hexSum(content), Mode: "FILE", AlwaysOn: on})
	}
	manifest, err := json.Marshal(map[string]any{
		"organization": "org-1", "remoteUrl": "github.com/sensey/demo", "number": number,
		"createdAt": "2026-10-24T10:00:00.000Z", "files": entries,
	})
	if err != nil {
		t.Fatal(err)
	}
	id := fmt.Sprintf("rel-%d", number)
	k.releases[id] = map[string]any{
		"releaseId": id, "repositoryId": "repo-1", "number": number, "manifest": string(manifest),
		"manifestSha256": hexSum(manifest), "signature": base64.StdEncoding.EncodeToString(ed25519.Sign(k.priv, manifest)), "keyId": "dev",
	}
	k.current = map[string]any{"repositoryId": "repo-1", "releaseId": id, "number": number, "manifestSha256": hexSum(manifest)}
}

func setupDelivery(t *testing.T) (*fakeKaya, string) {
	t.Helper()
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	kaya := &fakeKaya{priv: priv, releases: map[string]map[string]any{}, blobs: map[string][]byte{}}
	server := httptest.NewServer(kaya)
	t.Cleanup(server.Close)
	home := t.TempDir()
	for key, value := range map[string]string{
		"HOME": home, "XDG_CACHE_HOME": filepath.Join(home, "cache"), "XDG_STATE_HOME": filepath.Join(home, "state"),
		"XDG_CONFIG_HOME": filepath.Join(home, "config"), "KAYA_API_URL": server.URL, "KAYA_RECORDER_TOKEN": "kyt_test",
		"KAYA_DEV_TRUSTED_KEYS": "dev=" + base64.StdEncoding.EncodeToString(pub), "CLAUDE_CONFIG_DIR": filepath.Join(home, "claude"),
		envLaunched: "", envCheckout: "", envRelease: "", envReleaseState: "", envOffline: "", "CLAUDE_PROJECT_DIR": "",
	} {
		t.Setenv(key, value)
	}
	root := filepath.Join(t.TempDir(), "demo")
	if err := os.MkdirAll(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "kaya.yaml"), []byte("remoteUrl: git@github.com:sensey/demo.git\norganization: org-1\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	wd, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chdir(root); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.Chdir(wd) })
	return kaya, root
}

func run(t *testing.T, stdin string, args ...string) (string, error) {
	t.Helper()
	var out bytes.Buffer
	rootCmd.SetArgs(args)
	rootCmd.SetIn(strings.NewReader(stdin))
	rootCmd.SetOut(&out)
	rootCmd.SetErr(&out)
	syncJSON, statusJSON = false, false
	proposeOpts = proposeOptions{}
	// cobra keeps flag values and the changed mark between runs, and the list takes a search only
	// when the flag was given.
	lessonsOpts = lessonsOptions{}
	lessonsListCmd.Flags().Lookup("search").Changed = false
	_, err := rootCmd.ExecuteC()
	return out.String(), err
}

func hookEvent(t *testing.T, fields map[string]any) string {
	t.Helper()
	data, err := json.Marshal(fields)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func TestPreToolUse_refusesToolsWithoutAReleaseAndCallsNoAPI(t *testing.T) {
	kaya, root := setupDelivery(t)

	out, err := run(t, hookEvent(t, map[string]any{"cwd": root, "hook_event_name": "PreToolUse", "tool_name": "Bash", "tool_input": map[string]any{"command": "ls"}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, `"permissionDecision":"deny"`) || !strings.Contains(out, "No Kaya config on this machine for") {
		t.Fatalf("out = %s, err = %v", out, err)
	}
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Read", "tool_input": map[string]any{"file_path": "x"}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("Read: out = %q, err = %v", out, err)
	}
	if kaya.requests.Load() != 0 {
		t.Fatalf("the hook called the API %d times", kaya.requests.Load())
	}
}

func TestDelivery_syncThenHooksWorkFromLocalState(t *testing.T) {
	kaya, root := setupDelivery(t)
	kaya.publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")

	out, err := run(t, "", "sync")
	if err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}
	requests := kaya.requests.Load()

	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Edit", "tool_input": map[string]any{"file_path": filepath.Join(root, "AGENTS.md")}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, "kaya propose") {
		t.Fatalf("edit of a delivered path: %s, %v", out, err)
	}
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "src", "main.go")}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("edit of an own file: %q, %v", out, err)
	}

	t.Setenv(envLaunched, "1")
	t.Setenv(envCheckout, root)
	t.Setenv(envRelease, "1")
	t.Setenv(envReleaseState, "FRESH")
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, `"additionalContext":"Kaya config.`) || strings.Contains(out, "reloadSkills") {
		t.Fatalf("launched session start: %s, %v", out, err)
	}
	if got := kaya.pathsSince(requests); !slices.Equal(got, []string{"/kaya/delivery/lessons"}) {
		t.Fatalf("after the launcher the hooks called %v, want only the lessons call", got)
	}
	report, err := os.ReadFile(filepath.Join(os.Getenv("XDG_STATE_HOME"), "kaya", "queue", "sessions", "sess-1", "config-release.json"))
	if err != nil || !strings.Contains(string(report), `"number":1`) || !strings.Contains(string(report), `"deliveredBy":"LAUNCHER"`) {
		t.Fatalf("report = %s, %v", report, err)
	}

	// A start without the launcher syncs: a skill change switches and reloads skills.
	t.Setenv(envLaunched, "")
	kaya.publish(t, 2, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v2\n"}, "AGENTS.md")
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": "sess-2", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, `"reloadSkills":true`) || !strings.Contains(out, "- .agents/skills/demo/SKILL.md") {
		t.Fatalf("hook session start: %s, %v", out, err)
	}
	report, err = os.ReadFile(filepath.Join(os.Getenv("XDG_STATE_HOME"), "kaya", "queue", "sessions", "sess-2", "config-release.json"))
	if err != nil || !strings.Contains(string(report), `"number":2`) || !strings.Contains(string(report), `"deliveredBy":"HOOK"`) {
		t.Fatalf("report = %s, %v", report, err)
	}

	// An always-on change is staged with the restart message while this session lives on.
	kaya.publish(t, 3, map[string]string{"AGENTS.md": "rules v3\n", ".agents/skills/demo/SKILL.md": "skill v2\n"}, "AGENTS.md")
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": "sess-3", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, "Config release 3 changes always-on rules; restart Claude Code to use it.") {
		t.Fatalf("always-on change: %s, %v", out, err)
	}

	out, err = run(t, "", "status", "--json")
	if err != nil || !strings.Contains(out, `"state": "STALE"`) || !strings.Contains(out, `"staged": 3`) {
		t.Fatalf("status: %s, %v", out, err)
	}
}

func TestPreToolUse_refusesAnEditOfADeliveredConventionDoc(t *testing.T) {
	kaya, root := setupDelivery(t)
	doc := "docs/private/conventions/backend/32-soft-delete-convention.md"
	kaya.publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", doc: "# Soft delete\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}

	out, err := run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Edit", "tool_input": map[string]any{"file_path": filepath.Join(root, filepath.FromSlash(doc))}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, `"permissionDecision":"deny"`) || !strings.Contains(out, "kaya propose") {
		t.Fatalf("edit of a delivered doc: %s, %v", out, err)
	}
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "docs", "private", "projects", "notes.md")}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("edit of an undelivered doc: %q, %v", out, err)
	}
}

func TestPreToolUse_refusesAnEditOfADeliveredProjectAgentsFile(t *testing.T) {
	kaya, root := setupDelivery(t)
	kaya.publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", "projects/kagi/AGENTS.md": "# Kagi\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil || !strings.Contains(out, "config release 1 (FRESH)") {
		t.Fatalf("sync: %s, %v", out, err)
	}

	out, err := run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "projects", "kagi", "AGENTS.md")}}), "hook", "pre-tool-use")
	if err != nil || !strings.Contains(out, `"permissionDecision":"deny"`) || !strings.Contains(out, "kaya propose") {
		t.Fatalf("edit of a delivered project AGENTS.md: %s, %v", out, err)
	}
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "tool_name": "Write", "tool_input": map[string]any{"file_path": filepath.Join(root, "projects", "kagi", "operator", "AGENTS.md")}}), "hook", "pre-tool-use")
	if err != nil || out != "" {
		t.Fatalf("edit of a nested AGENTS.md that stays in git: %q, %v", out, err)
	}
}

func TestSessionStart_warnsWhenStartedBelowTheCheckoutRoot(t *testing.T) {
	kaya, root := setupDelivery(t)
	kaya.publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	sub := filepath.Join(root, "projects", "kagi")
	if err := os.MkdirAll(sub, 0o750); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, hookEvent(t, map[string]any{"cwd": sub, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")
	want := "Warning: this session started in projects/kagi, below the checkout root " + root
	if err != nil || !strings.Contains(out, want) || !strings.Contains(out, "Ask the user to restart Claude Code in "+root) {
		t.Fatalf("session below the root: %s, %v", out, err)
	}
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": "sess-2", "source": "startup"}), "hook", "session-start")
	if err != nil || !strings.Contains(out, `"additionalContext":"Kaya config.`) || strings.Contains(out, "Warning:") {
		t.Fatalf("session at the root: %s, %v", out, err)
	}
}

func TestIsExecutable_needsAnAbsoluteExecutableFile(t *testing.T) {
	dir := t.TempDir()
	binary := filepath.Join(dir, "claude")
	if err := os.WriteFile(binary, []byte("#!/bin/sh\n"), 0o700); err != nil {
		t.Fatal(err)
	}
	if !isExecutable(binary) || isExecutable("claude") || isExecutable("--resume") || isExecutable(dir) {
		t.Fatal("wrong wrapper argument rule")
	}
}

func TestWithoutSessionVars_dropsAnEnclosingSessionsRelease(t *testing.T) {
	kept := withoutSessionVars([]string{"PATH=/bin", envLaunched + "=1", envRelease + "=4", envReleaseState + "=FRESH", envCheckout + "=/x", envOffline + "=down"})
	if len(kept) != 1 || kept[0] != "PATH=/bin" {
		t.Fatalf("kept = %v", kept)
	}
}

func TestSessionStart_launchedOfflineSaysKayaIsUnreachable(t *testing.T) {
	kaya, root := setupDelivery(t)
	kaya.publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	t.Setenv(envLaunched, "1")
	t.Setenv(envCheckout, root)
	t.Setenv(envRelease, "1")
	t.Setenv(envReleaseState, "CACHED")
	t.Setenv(envOffline, offlineReason(errors.New("dial tcp 127.0.0.1:1:\nconnection refused")))

	out, err := run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": "sess-1", "source": "startup"}), "hook", "session-start")

	want := "Kaya is unreachable; this session runs on config release 1 of 2026-10-24T10:00:00.000Z. (dial tcp 127.0.0.1:1: connection refused)"
	if err != nil || !strings.Contains(out, want) {
		t.Fatalf("launched offline session start: %s, %v", out, err)
	}

	t.Setenv(envOffline, "")
	out, err = run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": "sess-2", "source": "startup"}), "hook", "session-start")
	if err != nil || strings.Contains(out, "unreachable") {
		t.Fatalf("launched online session start: %s, %v", out, err)
	}
}

func TestOfflineReason_isOneShortNonEmptyLine(t *testing.T) {
	if got := offlineReason(errors.New("a\n  b\tc")); got != "a b c" {
		t.Fatalf("reason = %q", got)
	}
	if got := offlineReason(errors.New(strings.Repeat("x", 2*maxOfflineReason))); len(got) != maxOfflineReason {
		t.Fatalf("length = %d", len(got))
	}
	if got := offlineReason(errors.New(" ")); got != "unknown" {
		t.Fatalf("empty reason = %q", got)
	}
}
