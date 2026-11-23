package sender

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
)

// The sender scenarios of the golden corpus (../capture/testdata, see its README.md) were
// recorded from the Python sender: each starts from the queue the case's hook calls left and runs
// `sender --once` against a fake ingest that answers from a list.

const (
	tokenA = "kyt_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
	tokenB = "kyt_BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
)

var corpusDir = filepath.Join("..", "capture", "testdata", "cases")

type fakeResponse struct {
	Status int `json:"status"`
	Body   any `json:"body"`
}

type scenarioRun struct {
	Token     string         `json:"token"`
	Responses []fakeResponse `json:"responses"`
}

type scenario struct {
	Name       string             `json:"name"`
	Runs       []scenarioRun      `json:"runs"`
	QueueFiles map[string]*string `json:"queue_files"`
	Usage      any                `json:"usage"`
}

type corpusCase struct {
	SessionID  string             `json:"session_id"`
	SSHAliases map[string]string  `json:"ssh_aliases"`
	QueueFiles map[string]*string `json:"queue_files"`
	StateFiles map[string]string  `json:"state_files"`
	Usage      any                `json:"usage"`
	Scenarios  []scenario         `json:"scenarios"`
}

type expectedRun struct {
	Token         string            `json:"token"`
	Requests      []expectedRequest `json:"requests"`
	Log           []string          `json:"log"`
	State         any               `json:"state"`
	Cursor        any               `json:"cursor"`
	Files         []string          `json:"files"`
	QueueAfter    map[string]string `json:"queueAfter"`
	UsageFileLeft bool              `json:"usageFileLeft"`
}

type expectedRequest struct {
	Authorized  bool   `json:"authorized"`
	Body        any    `json:"body"`
	ContentType string `json:"contentType"`
	Method      string `json:"method"`
	Path        string `json:"path"`
}

func TestSenderMatchesCorpus(t *testing.T) {
	entries, err := os.ReadDir(corpusDir)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		dir := filepath.Join(corpusDir, entry.Name())
		if _, err := os.Stat(filepath.Join(dir, "expected", "sender")); err != nil {
			continue
		}
		var c corpusCase
		readCorpus(t, filepath.Join(dir, "case.json"), &c)
		scenarios := append([]scenario{{Name: "ok", Runs: []scenarioRun{{Token: "A"}}}}, c.Scenarios...)
		for _, sc := range scenarios {
			t.Run(entry.Name()+"/"+sc.Name, func(t *testing.T) { replayScenario(t, dir, c, sc) })
		}
	}
}

func replayScenario(t *testing.T, dir string, c corpusCase, sc scenario) {
	base, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	state := filepath.Join(base, "xdg", "kaya")
	repo := filepath.Join(base, "repo")
	transcripts, err := filepath.Abs(filepath.Join(corpusDir, "..", "transcripts"))
	if err != nil {
		t.Fatal(err)
	}
	subst := map[string]string{"{{REPO}}": repo, "{{STATE}}": state, "{{TRANSCRIPTS}}": transcripts,
		"{{OUTSIDE}}": filepath.Join(base, "outside"), "{{HOME}}": filepath.Join(base, "home")}
	for key, value := range map[string]string{
		"HOME": filepath.Join(base, "home"), "XDG_STATE_HOME": filepath.Join(base, "xdg"), "XDG_CONFIG_HOME": filepath.Join(base, "config"),
		"GIT_CONFIG_GLOBAL": os.DevNull, "GIT_CONFIG_NOSYSTEM": "1", "GIT_CEILING_DIRECTORIES": base,
		"KAYA_SENDER": "off", "KAYA_RECORDER_TOKEN": "",
	} {
		t.Setenv(key, value)
	}
	expected := filepath.Join(dir, "expected")
	queue := capture.SessionDir(state, c.SessionID)
	seedQueue(t, expected, queue, subst)
	for rel, content := range c.StateFiles {
		writeFile(t, filepath.Join(state, rel), content)
	}
	queueFiles := map[string]*string{}
	for rel, content := range c.QueueFiles {
		queueFiles[rel] = content
	}
	for rel, content := range sc.QueueFiles {
		queueFiles[rel] = content
	}
	for rel, content := range queueFiles {
		path := filepath.Join(queue, rel)
		if content == nil {
			if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
				t.Fatal(err)
			}
			continue
		}
		writeFile(t, path, *content)
	}
	usage := c.Usage
	if sc.Usage != nil {
		usage = sc.Usage
	}
	if usage != nil {
		data, err := json.Marshal(usage)
		if err != nil {
			t.Fatal(err)
		}
		writeFile(t, capture.UsagePath(state, c.SessionID), string(data))
	}

	var want struct {
		Runs []expectedRun `json:"runs"`
	}
	readCorpus(t, filepath.Join(expected, "sender", sc.Name+".json"), &want)
	// A case whose session is never sent has no batch.json.
	var batchBody any
	var sentRemote string
	if _, err := os.Stat(filepath.Join(expected, "batch.json")); err == nil {
		readCorpus(t, filepath.Join(expected, "batch.json"), &batchBody)
		batchBody = expand(batchBody, subst)
		delete(batchBody.(map[string]any)["summary"].(map[string]any), "project")
		sentRemote, _ = batchBody.(map[string]any)["repository"].(map[string]any)["remoteUrl"].(string)
	}
	// The Python sender sent every session. This sender sends one only under a remote on the cached
	// repository list, so the case's list holds the repository the Python sender sent, beside
	// another one. A recorded ssh host alias is then sent resolved, as the list holds it and as the
	// Python sender sent it. A case without a remote, or with one no list can hold, such as a local
	// path, keeps its session queued and unsent.
	listed := seed.NormalizeRemote(sentRemote)
	remotes := []string{"github.com/example/other"}
	if listed != "" {
		remotes = append(remotes, listed)
	}
	if err := delivery.SaveRepositoryList(delivery.RepositoryList{OrganizationID: "org-example", Remotes: remotes}); err != nil {
		t.Fatal(err)
	}

	// The resolver maps the case's aliases as the ssh config of the recorded case did.
	resolver := &seed.RemoteResolver{Lookup: func(host string) (string, error) {
		if host == "broken-host" {
			return "", errors.New("ssh -G broken-host exited 255: ssh: broken")
		}
		if resolved, ok := c.SSHAliases[host]; ok {
			return resolved, nil
		}
		return strings.ToLower(host), nil
	}}
	for i, r := range sc.Runs {
		if i >= len(want.Runs) {
			t.Fatalf("run %d has no expected result", i)
		}
		w := want.Runs[i]
		if listed == "" {
			w = keptUnsent(w, c.SessionID, usage != nil)
		}
		token := tokenA
		if r.Token == "B" {
			token = tokenB
		}
		fake := newFakeIngest(r.Responses)
		server := httptest.NewServer(fake)
		logPath := filepath.Join(state, capture.LogFile)
		if err := os.Remove(logPath); err != nil && !errors.Is(err, os.ErrNotExist) {
			t.Fatal(err)
		}
		err := Run(context.Background(), Config{
			APIURL:   server.URL,
			Token:    func(context.Context) (string, error) { return token, nil },
			Once:     true,
			Resolver: resolver,
			Hostname: func() (string, error) { return "golden-host", nil },
		})
		server.Close()
		if err != nil {
			t.Fatalf("run %d: %v", i, err)
		}

		var gotRequests []any
		for _, req := range fake.requests {
			if req.authorization != "Bearer "+token {
				t.Errorf("run %d: Authorization header is not the run's token", i)
			}
			gotRequests = append(gotRequests, map[string]any{"method": req.method, "path": req.path, "contentType": req.contentType, "body": req.body})
		}
		var wantRequests []any
		for _, req := range w.Requests {
			body := expand(req.Body, subst)
			if text, ok := body.(string); ok && text == "same as ../batch.json" {
				body = batchBody
			} else if object, ok := body.(map[string]any); ok {
				delete(object["summary"].(map[string]any), "project")
			}
			wantRequests = append(wantRequests, map[string]any{"method": req.Method, "path": req.Path, "contentType": req.ContentType, "body": body})
		}
		compareJSON(t, fmt.Sprintf("run %d requests", i), wantRequests, gotRequests)
		compareLog(t, i, expand(stringsAny(w.Log), subst), readLog(t, logPath))
		for _, name := range []string{logPath, filepath.Join(state, capture.StateFile)} {
			if data, err := os.ReadFile(name); err == nil && bytes.Contains(data, []byte(token)) {
				t.Errorf("run %d: %s holds the token", i, name)
			}
		}
		compareJSON(t, fmt.Sprintf("run %d state", i), w.State, normalized(readOptional(t, filepath.Join(state, capture.StateFile)), "updatedAt", "lastSentAt"))
		compareJSON(t, fmt.Sprintf("run %d cursor", i), w.Cursor, normalized(readOptional(t, filepath.Join(queue, cursorFile)), "sentAt"))
		compareJSON(t, fmt.Sprintf("run %d files", i), w.Files, tree(t, queue))
		if len(w.QueueAfter) > 0 {
			got := map[string]any{}
			want := map[string]any{}
			for name, content := range w.QueueAfter {
				want[name] = jsonLines(expand(content, subst).(string))
				data, err := os.ReadFile(filepath.Join(queue, name))
				if err != nil {
					t.Fatal(err)
				}
				got[name] = jsonLines(string(data))
			}
			compareJSON(t, fmt.Sprintf("run %d queue after", i), want, got)
		}
		_, statErr := os.Stat(capture.UsagePath(state, c.SessionID))
		if left := statErr == nil; left != w.UsageFileLeft {
			t.Errorf("run %d: usage file left %v, want %v", i, left, w.UsageFileLeft)
		}
	}
}

// seedQueue writes the session's queue as the corpus recorded it after the case's hook calls.
func seedQueue(t *testing.T, expected, queue string, subst map[string]string) {
	t.Helper()
	afterEdits := filepath.Join(expected, "queue_after_edits.json")
	if _, err := os.Stat(afterEdits); err == nil {
		var files map[string]any
		readCorpus(t, afterEdits, &files)
		for name, content := range expand(files, subst).(map[string]any) {
			writeFile(t, filepath.Join(queue, name), content.(string))
		}
	} else {
		var events []any
		readCorpus(t, filepath.Join(expected, "events.json"), &events)
		var b strings.Builder
		for _, event := range expand(events, subst).([]any) {
			line, err := encodeCompact(event)
			if err != nil {
				t.Fatal(err)
			}
			b.Write(line)
			b.WriteByte('\n')
		}
		writeFile(t, filepath.Join(queue, capture.EventsFile), b.String())
	}
	for _, name := range []string{capture.SummaryFile, capture.SessionFile} {
		var value any
		readCorpus(t, filepath.Join(expected, name), &value)
		if err := writeJSON(filepath.Join(queue, name), expand(value, subst)); err != nil {
			t.Fatal(err)
		}
	}
	var snapshots map[string]struct {
		Content any    `json:"content"`
		Mtime   string `json:"mtime"`
	}
	readCorpus(t, filepath.Join(expected, "snapshots.json"), &snapshots)
	for rel, snap := range snapshots {
		path := filepath.Join(queue, filepath.FromSlash(rel))
		writeFile(t, path, expand(snap.Content, subst).(string))
		at, err := time.Parse(isoSeconds, snap.Mtime)
		if err != nil {
			t.Fatal(err)
		}
		if err := os.Chtimes(path, at, at); err != nil {
			t.Fatal(err)
		}
	}
	// Marker files the hook leaves, such as .token-warned, carry no content.
	var files []string
	readCorpus(t, filepath.Join(expected, "files.json"), &files)
	for _, rel := range files {
		path := filepath.Join(queue, filepath.FromSlash(rel))
		if _, err := os.Stat(path); errors.Is(err, os.ErrNotExist) {
			writeFile(t, path, "")
		}
	}
}

type recordedRequest struct {
	method, path, contentType, authorization string
	body                                     any
}

// fakeIngest answers from a list, then accepts every batch and acknowledges its highest seq.
type fakeIngest struct {
	mu        sync.Mutex
	responses []fakeResponse
	requests  []recordedRequest
}

func newFakeIngest(responses []fakeResponse) *fakeIngest {
	return &fakeIngest{responses: append([]fakeResponse(nil), responses...)}
}

func (f *fakeIngest) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	raw, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	var body map[string]any
	if err := decodeNumbers(raw, &body); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	f.requests = append(f.requests, recordedRequest{method: r.Method, path: r.URL.Path, contentType: r.Header.Get("Content-Type"),
		authorization: r.Header.Get("Authorization"), body: body})
	if len(f.responses) > 0 {
		next := f.responses[0]
		f.responses = f.responses[1:]
		w.WriteHeader(next.Status)
		if next.Body != nil {
			_ = json.NewEncoder(w).Encode(next.Body)
		}
		return
	}
	var highest int64
	events, _ := body["events"].([]any)
	for _, event := range events {
		if seq, err := event.(map[string]any)["seq"].(json.Number).Int64(); err == nil {
			highest = max(highest, seq)
		}
	}
	_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": map[string]any{"acknowledgedSeq": highest, "sessionId": body["sessionId"]}})
}

var logPrefix = regexp.MustCompile(`^\S+ \d+ `)

func readLog(t *testing.T, path string) []string {
	t.Helper()
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		t.Fatal(err)
	}
	var out []string
	for _, line := range strings.Split(strings.TrimRight(string(data), "\n"), "\n") {
		if line != "" {
			out = append(out, logPrefix.ReplaceAllString(line, ""))
		}
	}
	return out
}

// compareLog compares the log lines where the CLI words them as the recorder did. Where the CLI
// says more or the Go library words an error differently, the line must still name the same
// thing.
func compareLog(t *testing.T, run int, wantAny any, got []string) {
	t.Helper()
	var want []string
	for _, line := range wantAny.([]any) {
		// The Python sender warned when ssh could not resolve the recorded remote's host. This
		// sender never logs that warning, since it names the remote; a remote the list holds as
		// recorded is sent without asking ssh.
		if strings.HasPrefix(line.(string), "ssh -G ") {
			continue
		}
		want = append(want, line.(string))
	}
	if len(want) != len(got) {
		t.Errorf("run %d: log\n got %q\nwant %q", run, got, want)
		return
	}
	for i := range want {
		w := strings.ReplaceAll(strings.TrimRight(want[i], " "), " ;", ";")
		g := got[i]
		switch {
		case strings.Contains(w, "403 KAY_SEC_007"):
			w = strings.Replace(w, "KAY_SEC_007;", `KAY_SEC_007: no Kaya user holds the git author email "dev@example.com";`, 1)
		case strings.Contains(w, "unreadable, reported as UNVERIFIED:"):
			w, _, _ = strings.Cut(w, "UNVERIFIED:")
			g, _, _ = strings.Cut(g, "UNVERIFIED:")
		}
		if w != g {
			t.Errorf("run %d: log line %d\n got %q\nwant %q", run, i, got[i], want[i])
		}
	}
}

// keptUnsent is the expected run of a session whose repository the list does not hold: Kaya
// receives nothing, the queue keeps the session without a cursor, usage stays, and the log names
// the session in one line.
func keptUnsent(w expectedRun, sessionID string, usage bool) expectedRun {
	files := []string{}
	for _, name := range w.Files {
		if name != cursorFile {
			files = append(files, name)
		}
	}
	return expectedRun{Token: w.Token, Log: []string{sessionID + ": not sent, " + unlisted}, Files: files, UsageFileLeft: usage}
}

func readOptional(t *testing.T, path string) any {
	t.Helper()
	var value any
	found, err := readJSON(path, &value)
	if err != nil {
		t.Fatal(err)
	}
	if !found {
		return nil
	}
	return value
}

// normalized drops the times the corpus leaves out and replaces token fingerprints, which need
// not equal the recorder's.
func normalized(value any, drop ...string) any {
	object, ok := value.(map[string]any)
	if !ok {
		return value
	}
	for _, key := range drop {
		delete(object, key)
	}
	if _, ok := object["refusedFingerprint"]; ok {
		object["refusedFingerprint"] = "<fingerprint>"
	}
	if p, ok := object["parked"].(map[string]any); ok {
		delete(p, "at")
		p["tokenFingerprint"] = "<fingerprint>"
	}
	return object
}

func jsonLines(text string) []any {
	out := []any{}
	for _, line := range strings.SplitAfter(text, "\n") {
		if line == "" {
			continue
		}
		var value any
		if err := decodeNumbers([]byte(strings.TrimRight(line, "\n")), &value); err != nil {
			value = map[string]any{"$raw": line}
		}
		out = append(out, value)
	}
	return out
}

func tree(t *testing.T, root string) []string {
	t.Helper()
	found := []string{}
	err := filepath.WalkDir(root, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() || entry.Name() == ".lock" || strings.Contains(entry.Name(), ".tmp-") {
			return nil
		}
		rel, err := filepath.Rel(root, path)
		found = append(found, filepath.ToSlash(rel))
		return err
	})
	if err != nil {
		t.Fatal(err)
	}
	sort.Strings(found)
	return found
}

func compareJSON(t *testing.T, what string, want, got any) {
	t.Helper()
	wantJSON, _ := json.MarshalIndent(want, "", " ")
	gotJSON, _ := json.MarshalIndent(got, "", " ")
	if !bytes.Equal(wantJSON, gotJSON) {
		t.Errorf("%s differ\n got %s\nwant %s", what, clip(gotJSON), clip(wantJSON))
	}
}

func clip(data []byte) string {
	if len(data) > 4000 {
		return string(data[:4000]) + "..."
	}
	return string(data)
}

func stringsAny(lines []string) any {
	out := make([]any, len(lines))
	for i, line := range lines {
		out[i] = line
	}
	return out
}

func readCorpus(t *testing.T, path string, target any) {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var raw any
	if err := decodeNumbers(data, &raw); err != nil {
		t.Fatalf("%s: %v", path, err)
	}
	expanded, err := json.Marshal(expandLarge(raw))
	if err != nil {
		t.Fatal(err)
	}
	decoder := json.NewDecoder(bytes.NewReader(expanded))
	decoder.UseNumber()
	if err := decoder.Decode(target); err != nil {
		t.Fatalf("%s: %v", path, err)
	}
}

// expandLarge restores the strings the corpus stores as $repeat and $concat objects.
func expandLarge(value any) any {
	switch v := value.(type) {
	case []any:
		for i := range v {
			v[i] = expandLarge(v[i])
		}
		return v
	case map[string]any:
		if unit, ok := v["$repeat"].(string); ok {
			times, _ := v["$times"].(json.Number).Int64()
			return strings.Repeat(unit, int(times))
		}
		if parts, ok := v["$concat"].([]any); ok {
			var b strings.Builder
			for _, part := range parts {
				b.WriteString(expandLarge(part).(string))
			}
			return b.String()
		}
		for key := range v {
			v[key] = expandLarge(v[key])
		}
		return v
	}
	return value
}

// expand replaces the corpus placeholders in every string of value.
func expand(value any, subst map[string]string) any {
	switch v := value.(type) {
	case string:
		for key, replacement := range subst {
			v = strings.ReplaceAll(v, key, replacement)
		}
		return v
	case []any:
		out := make([]any, len(v))
		for i := range v {
			out[i] = expand(v[i], subst)
		}
		return out
	case map[string]any:
		out := make(map[string]any, len(v))
		for key, item := range v {
			out[key] = expand(item, subst)
		}
		return out
	}
	return value
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestSecondSenderFindsTheLockHeld(t *testing.T) {
	base := t.TempDir()
	t.Setenv("XDG_STATE_HOME", base)
	state, err := capture.EnsureStateDir()
	if err != nil {
		t.Fatal(err)
	}
	free, err := lockFree(filepath.Join(state, lockFile))
	if err != nil || !free {
		t.Fatalf("lockFree = %v, %v; want a free lock", free, err)
	}
	held, err := os.OpenFile(filepath.Join(state, lockFile), os.O_RDWR, 0o600)
	if err != nil {
		t.Fatal(err)
	}
	defer func() { _ = held.Close() }()
	if err := flockNB(held); err != nil {
		t.Fatal(err)
	}
	called := false
	err = Run(context.Background(), Config{APIURL: "https://kaya.invalid", Token: func(context.Context) (string, error) {
		called = true
		return tokenA, nil
	}})
	if err != nil || called {
		t.Fatalf("Run with the lock held = %v, token read %v; want an exit before the token", err, called)
	}
}

func flockNB(file *os.File) error {
	return syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB)
}

func TestPlainHTTPIsRefused(t *testing.T) {
	t.Setenv("XDG_STATE_HOME", t.TempDir())
	err := Run(context.Background(), Config{APIURL: "http://kaya.example.com", Once: true,
		Token:  func(context.Context) (string, error) { return tokenA, nil },
		Client: func(string, string) Poster { t.Fatal("a client was built for a plain-http URL"); return nil },
	})
	if err != nil {
		t.Fatal(err)
	}
}

func TestStartDetachedHonoursSenderOff(t *testing.T) {
	t.Setenv("XDG_STATE_HOME", t.TempDir())
	t.Setenv("KAYA_SENDER", "off")
	started, err := StartDetached()
	if err != nil || started {
		t.Fatalf("StartDetached = %v, %v; want nothing started", started, err)
	}
}
