package capture

import (
	"bytes"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
	"time"
)

// The golden corpus in testdata/ was recorded from the Python recorder; see testdata/README.md.

// divergentSteps are steps where the CLI deliberately differs from the recorder: it refuses a
// session id that is not valid instead of stripping the characters it does not accept.
var divergentSteps = map[string]map[int]string{
	"hook-errors": {5: "abcd"},
}

var goldenAccount = map[string]string{
	"billingType":      "stripe_subscription",
	"emailAddress":     "dev@example.com",
	"organizationName": "Korur",
	"organizationType": "claude_max",
	"organizationUuid": "11111111-2222-3333-4444-555555555555",
	"seatTier":         "max_5x",
}

type corpusFile struct {
	Path    string `json:"path"`
	Content any    `json:"content"`
	Mtime   string `json:"mtime"`
}

type corpusStep struct {
	At       string            `json:"at"`
	Hook     map[string]any    `json:"hook"`
	RawStdin *string           `json:"raw_stdin"`
	Files    []corpusFile      `json:"files"`
	Env      map[string]string `json:"env"`
}

type corpusCase struct {
	SessionID  string            `json:"session_id"`
	Branch     string            `json:"branch"`
	Remote     *string           `json:"remote"`
	Email      *string           `json:"email"`
	Account    bool              `json:"account"`
	Worktree   *string           `json:"worktree"`
	RepoFiles  []corpusFile      `json:"repo_files"`
	QueueFiles map[string]string `json:"queue_files"`
	StateFiles map[string]string `json:"state_files"`
	Usage      any               `json:"usage"`
	Steps      []corpusStep      `json:"steps"`
	QueueEdits []queueEdit       `json:"queue_edits"`
}

// queueEdit damages a queue file after the last step; the recorded events.json shows the result.
type queueEdit struct {
	File        string  `json:"file"`
	ReplaceLine int     `json:"replace_line"`
	With        string  `json:"with"`
	Append      *string `json:"append"`
}

type expectedStep struct {
	Stderr []string `json:"stderr"`
}

func TestRecordMatchesCorpus(t *testing.T) {
	names, err := os.ReadDir(filepath.Join("testdata", "cases"))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git is not installed")
	}
	transcripts, err := filepath.Abs(filepath.Join("testdata", "transcripts"))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range names {
		name := entry.Name()
		t.Run(name, func(t *testing.T) { replayCase(t, name, transcripts) })
	}
}

func replayCase(t *testing.T, name, transcripts string) {
	dir := filepath.Join("testdata", "cases", name)
	var c corpusCase
	readCorpus(t, filepath.Join(dir, "case.json"), &c)

	base, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	repo := filepath.Join(base, "repo")
	state := filepath.Join(base, "xdg", "kaya")
	claude := filepath.Join(base, "claude")
	outside := filepath.Join(base, "outside")
	for _, d := range []string{repo, claude, outside, filepath.Join(base, "home")} {
		if err := os.MkdirAll(d, 0o700); err != nil {
			t.Fatal(err)
		}
	}
	subst := map[string]string{"{{REPO}}": repo, "{{STATE}}": state, "{{TRANSCRIPTS}}": transcripts, "{{OUTSIDE}}": outside, "{{HOME}}": filepath.Join(base, "home")}
	isolate(t, base, claude)

	gitRun(t, repo, "init", "-q", "-b", c.Branch)
	// Record queues for any checkout; the hook decides by the remotes whether it is called, so the
	// corpus, which pins the Python recorder that recorded every checkout, replays Record directly.
	gitRun(t, repo, "commit", "-q", "--allow-empty", "-m", "init")
	// The hook passes the remote that recognised the checkout; the corpus's checkout has origin only.
	remote := ""
	if c.Remote != nil {
		gitRun(t, repo, "remote", "add", "origin", *c.Remote)
		remote = *c.Remote
	}
	if c.Email != nil {
		gitRun(t, repo, "config", "user.email", *c.Email)
	}
	if c.Worktree != nil {
		gitRun(t, repo, "worktree", "add", "-q", ".claude/worktrees/"+*c.Worktree, "-b", *c.Worktree)
	}
	if c.Account {
		data, _ := json.Marshal(map[string]any{"oauthAccount": goldenAccount, "primaryApiKey": "private-marker", "userID": "abc"})
		writeFile(t, filepath.Join(claude, ".claude.json"), string(data))
	}
	queue := SessionDir(state, c.SessionID)
	for rel, content := range c.StateFiles {
		writeFile(t, filepath.Join(state, rel), content)
	}
	for rel, content := range c.QueueFiles {
		writeFile(t, filepath.Join(queue, rel), content)
	}
	if c.Usage != nil {
		data, _ := json.Marshal(c.Usage)
		writeFile(t, filepath.Join(state, "queue", "usage", c.SessionID+".json"), string(data))
	}

	var expectedSteps []expectedStep
	readCorpus(t, filepath.Join(dir, "expected", "steps.json"), &expectedSteps)
	steps := c.Steps
	if len(c.RepoFiles) > 0 {
		steps[0].Files = append(append([]corpusFile{}, c.RepoFiles...), steps[0].Files...)
	}
	divergent := divergentSteps[name]
	for i, step := range steps {
		for _, f := range step.Files {
			path := filepath.Join(repo, expand(f.Path, subst).(string))
			writeFile(t, path, expand(f.Content, subst).(string))
			mtime := parseISO(t, f.Mtime)
			if err := os.Chtimes(path, mtime, mtime); err != nil {
				t.Fatal(err)
			}
		}
		for key, value := range step.Env {
			t.Setenv(key, expand(value, subst).(string))
		}
		var warnings bytes.Buffer
		err := recordStep(step, remote, subst, parseISO(t, step.At), &warnings)
		for key := range step.Env {
			t.Setenv(key, "")
		}
		got := lines(warnings.String())
		var want []string
		wantErr := false
		for _, line := range expectedSteps[i].Stderr {
			if strings.HasPrefix(line, "kaya recorder: nothing recorded:") {
				wantErr = true
				continue
			}
			want = append(want, expand(line, subst).(string))
		}
		if _, ok := divergent[i]; ok {
			wantErr = true
		}
		if wantErr != (err != nil) {
			t.Errorf("step %d: error %v, want an error: %v", i, err, wantErr)
		}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("step %d: warnings\n got %q\nwant %q", i, got, want)
		}
	}

	expected := filepath.Join(dir, "expected")
	if _, err := os.Stat(filepath.Join(expected, "sessions.json")); err == nil {
		compareSessions(t, expected, state, subst, divergent)
		return
	}
	applyQueueEdits(t, queue, c.QueueEdits)
	compareJSON(t, "events", readExpected(t, filepath.Join(expected, "events.json"), subst), readEvents(t, filepath.Join(queue, EventsFile)))
	wantSummary := readExpected(t, filepath.Join(expected, "summary.json"), subst).(map[string]any)
	delete(wantSummary, "project")
	compareJSON(t, "summary", wantSummary, readActual(t, filepath.Join(queue, SummaryFile)))
	compareJSON(t, "session", readExpected(t, filepath.Join(expected, "session.json"), subst), readActual(t, filepath.Join(queue, SessionFile)))
	compareJSON(t, "snapshots", readExpected(t, filepath.Join(expected, "snapshots.json"), subst), snapshots(t, queue))
	compareJSON(t, "files", readExpected(t, filepath.Join(expected, "files.json"), subst), tree(t, queue))
}

func applyQueueEdits(t *testing.T, queue string, edits []queueEdit) {
	t.Helper()
	for _, edit := range edits {
		path := filepath.Join(queue, edit.File)
		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		text := string(data)
		if edit.ReplaceLine > 0 {
			rows := strings.SplitAfter(text, "\n")
			rows[edit.ReplaceLine-1] = edit.With
			text = strings.Join(rows, "")
		}
		if edit.Append != nil {
			text += *edit.Append
		}
		writeFile(t, path, text)
	}
}

func recordStep(step corpusStep, remote string, subst map[string]string, now time.Time, warnings *bytes.Buffer) error {
	var in map[string]any
	if step.RawStdin != nil {
		decoded, err := Decode([]byte(*step.RawStdin))
		if err != nil {
			return err
		}
		in = decoded
	} else {
		data, err := json.Marshal(expand(any(step.Hook), subst))
		if err != nil {
			return err
		}
		if in, err = Decode(data); err != nil {
			return err
		}
	}
	_, err := Record(in, remote, now, warnings)
	return err
}

// isolate points every directory the capture reads or writes into base, so the developer's own
// state, git config and Claude Code config are never touched.
func isolate(t testing.TB, base, claude string) {
	t.Helper()
	for key, value := range map[string]string{
		"HOME": filepath.Join(base, "home"), "XDG_STATE_HOME": filepath.Join(base, "xdg"), "CLAUDE_CONFIG_DIR": claude,
		"GIT_CONFIG_GLOBAL": os.DevNull, "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "g", "GIT_AUTHOR_EMAIL": "g@example.com",
		"GIT_COMMITTER_NAME": "g", "GIT_COMMITTER_EMAIL": "g@example.com", "KAYA_SENDER": "off", "CLAUDE_PROJECT_DIR": "",
	} {
		t.Setenv(key, value)
	}
	saved := hostname
	hostname = func() (string, error) { return "golden-host", nil }
	t.Cleanup(func() { hostname = saved })
}

func compareSessions(t *testing.T, expected, state string, subst map[string]string, divergent map[int]string) {
	t.Helper()
	want := readExpected(t, filepath.Join(expected, "sessions.json"), subst).(map[string]any)
	wantFiles := readExpected(t, filepath.Join(expected, "files.json"), subst).([]any)
	for _, id := range divergent {
		delete(want, id)
		kept := wantFiles[:0]
		for _, f := range wantFiles {
			if !strings.HasPrefix(f.(string), "queue/sessions/"+id+"/") {
				kept = append(kept, f)
			}
		}
		wantFiles = kept
	}
	got := map[string]any{}
	entries, _ := os.ReadDir(SessionsDir(state))
	for _, entry := range entries {
		got[entry.Name()] = readEvents(t, filepath.Join(SessionsDir(state), entry.Name(), EventsFile))
	}
	compareJSON(t, "sessions", want, got)
	compareJSON(t, "files", wantFiles, tree(t, state))
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
	if len(data) > 6000 {
		return string(data[:6000]) + "..."
	}
	return string(data)
}

func readCorpus(t testing.TB, path string, target any) {
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

func readExpected(t testing.TB, path string, subst map[string]string) any {
	t.Helper()
	var value any
	readCorpus(t, path, &value)
	return expand(value, subst)
}

func readActual(t *testing.T, path string) any {
	t.Helper()
	var value any
	if _, err := readJSON(path, &value); err != nil {
		t.Fatal(err)
	}
	return value
}

func readEvents(t *testing.T, path string) []any {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	events := []any{}
	for _, line := range lines(string(data)) {
		var event any
		if err := decodeNumbers([]byte(line), &event); err != nil {
			// The corpus keeps a damaged line as its raw text.
			event = map[string]any{"$raw": line}
		}
		events = append(events, event)
	}
	return events
}

func snapshots(t *testing.T, queue string) map[string]any {
	t.Helper()
	found := map[string]any{}
	for _, item := range tree(t, queue) {
		rel := item.(string)
		if !strings.HasPrefix(rel, "handoffs/") && !strings.HasPrefix(rel, "plans/") {
			continue
		}
		full := filepath.Join(queue, rel)
		data, err := os.ReadFile(full)
		if err != nil {
			t.Fatal(err)
		}
		info, err := os.Stat(full)
		if err != nil {
			t.Fatal(err)
		}
		found[rel] = map[string]any{"content": string(data), "mtime": isoOf(info.ModTime())}
	}
	return found
}

func tree(t *testing.T, root string) []any {
	t.Helper()
	var found []string
	err := filepath.WalkDir(root, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() || entry.Name() == lockFile || strings.Contains(entry.Name(), ".tmp-") {
			return nil
		}
		rel, err := filepath.Rel(root, path)
		found = append(found, filepath.ToSlash(rel))
		return err
	})
	if err != nil && !os.IsNotExist(err) {
		t.Fatal(err)
	}
	sort.Strings(found)
	out := []any{}
	for _, f := range found {
		out = append(out, f)
	}
	return out
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

func gitRun(t testing.TB, dir string, args ...string) {
	t.Helper()
	cmd := exec.Command("git", append([]string{"-C", dir}, args...)...)
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("git %v: %v\n%s", args, err, out)
	}
}

func writeFile(t testing.TB, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func parseISO(t testing.TB, text string) time.Time {
	t.Helper()
	at, err := time.Parse(isoSeconds, text)
	if err != nil {
		t.Fatal(err)
	}
	return at
}

func lines(text string) []string {
	var out []string
	for _, line := range strings.Split(text, "\n") {
		if line != "" {
			out = append(out, line)
		}
	}
	return out
}
