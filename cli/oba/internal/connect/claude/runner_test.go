package claude

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

const fixture = "testdata/stream.jsonl"

func openFixture(t *testing.T) *os.File {
	t.Helper()
	f, err := os.Open(fixture)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = f.Close() })
	return f
}

// candidate is the shape the recorded stream's result carries.
type candidate struct {
	Name    string `json:"name"`
	Website string `json:"website"`
	Country string `json:"country"`
	Reason  string `json:"reason"`
}

func decodeCandidates(t *testing.T, raw json.RawMessage) []candidate {
	t.Helper()
	var out struct {
		Candidates []candidate `json:"candidates"`
	}
	if err := json.Unmarshal(raw, &out); err != nil {
		t.Fatalf("result %s: %v", raw, err)
	}
	return out.Candidates
}

func collect(lines *[]string) ProgressFunc {
	return func(line string) { *lines = append(*lines, line) }
}

func TestParseRecordedStream(t *testing.T) {
	var progress []string
	raw, err := Parse(openFixture(t), collect(&progress))
	if err != nil {
		t.Fatal(err)
	}
	got := decodeCandidates(t, raw)
	wantProgress := []string{
		"Searching: open-source database company headquartered Netherlands OR Germany (MonetDB Solutions Amsterdam, ArangoDB Cologne)",
		"Reading: https://www.monetdbsolutions.com/",
	}
	if !reflect.DeepEqual(progress, wantProgress) {
		t.Errorf("progress = %q, want %q", progress, wantProgress)
	}
	if len(got) != 2 {
		t.Fatalf("got %d candidates, want 2: %+v", len(got), got)
	}
	if got[0].Name != "Weaviate" || got[0].Website != "https://weaviate.io" || got[0].Country != "NL" || got[0].Reason == "" {
		t.Errorf("first candidate = %+v", got[0])
	}
	if got[1].Website != "https://www.monetdbsolutions.com" {
		t.Errorf("second candidate = %+v", got[1])
	}
}

func TestParseNilProgress(t *testing.T) {
	if _, err := Parse(openFixture(t), nil); err != nil {
		t.Fatal(err)
	}
}

func TestParseFallsBackToResultText(t *testing.T) {
	cases := map[string]string{
		"plain":  `{"candidates":[{"name":"Acme","website":"https://acme.example","country":"NL","reason":"fits"}]}`,
		"fenced": "```json\n{\"candidates\":[{\"name\":\"Acme\",\"website\":\"https://acme.example\",\"country\":\"NL\",\"reason\":\"fits\"}]}\n```",
	}
	for name, text := range cases {
		t.Run(name, func(t *testing.T) {
			quoted, err := json.Marshal(text)
			if err != nil {
				t.Fatal(err)
			}
			stream := `{"type":"system","subtype":"init"}` + "\n" +
				`{"type":"result","subtype":"success","is_error":false,"result":` + string(quoted) + `,"structured_output":null}` + "\n"
			raw, err := Parse(strings.NewReader(stream), nil)
			if err != nil {
				t.Fatal(err)
			}
			got := decodeCandidates(t, raw)
			want := []candidate{{Name: "Acme", Website: "https://acme.example", Country: "NL", Reason: "fits"}}
			if !reflect.DeepEqual(got, want) {
				t.Errorf("got %+v, want %+v", got, want)
			}
		})
	}
}

func TestParseCleansResultAndProgress(t *testing.T) {
	stream := `{"type":"assistant","message":{"content":[` +
		`{"type":"tool_use","name":"WebSearch","input":{"query":"acme\u001b]0;pwned\u0007 nl"}},` +
		`{"type":"tool_use","name":"WebFetch","input":{"url":"https://acme.example/\u202etxt"}}]}}` + "\n" +
		`{"type":"result","subtype":"success","is_error":false,"structured_output":` +
		`{"candidates":[{"name":"Acme\u001b[2J","website":"https://acme.example","country":"NL","reason":"fits\u009b31m"}]}}` + "\n"
	var progress []string
	raw, err := Parse(strings.NewReader(stream), collect(&progress))
	if err != nil {
		t.Fatal(err)
	}
	got := decodeCandidates(t, raw)
	want := []candidate{{Name: "Acme", Website: "https://acme.example", Country: "NL", Reason: "fits"}}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %+v, want %+v", got, want)
	}
	wantProgress := []string{"Searching: acme nl", "Reading: https://acme.example/txt"}
	if !reflect.DeepEqual(progress, wantProgress) {
		t.Errorf("progress = %q, want %q", progress, wantProgress)
	}
}

func TestParseCleansErrorMessage(t *testing.T) {
	stream := `{"type":"result","subtype":"error_during_execution","is_error":true,"result":"bo\u001b[31mom"}`
	_, err := Parse(strings.NewReader(stream), nil)
	var resultErr *ResultError
	if !errors.As(err, &resultErr) || resultErr.Message != "boom" {
		t.Fatalf("err = %v", err)
	}
}

func TestParseIsError(t *testing.T) {
	stream := `{"type":"result","subtype":"error_max_turns","is_error":true,"errors":["too many turns"]}`
	_, err := Parse(strings.NewReader(stream), nil)
	var resultErr *ResultError
	if !errors.As(err, &resultErr) {
		t.Fatalf("err = %v, want *ResultError", err)
	}
	if resultErr.Subtype != "error_max_turns" || resultErr.Message != "too many turns" {
		t.Errorf("result error = %+v", resultErr)
	}
}

func TestParseFailures(t *testing.T) {
	cases := map[string]string{
		"no result":       `{"type":"system","subtype":"init"}`,
		"not json":        "warning: something\n",
		"bad result text": `{"type":"result","is_error":false,"result":"no candidates here"}`,
		"no output":       `{"type":"result","is_error":false}`,
	}
	for name, stream := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := Parse(strings.NewReader(stream), nil); err == nil {
				t.Error("want an error")
			}
		})
	}
}

func TestArgs(t *testing.T) {
	want := []string{
		"-p", "--restricted", "--tools", "WebSearch,WebFetch", "--allowedTools", "WebSearch,WebFetch",
		"--strict-mcp-config", "--disable-slash-commands", "--no-session-persistence",
		"--permission-mode", "dontAsk", "--output-format", "stream-json", "--verbose", "--json-schema", "{}",
	}
	if got := Args("{}"); !reflect.DeepEqual(got, want) {
		t.Errorf("Args = %q, want %q", got, want)
	}
}

// fakeClaude makes Run start this test binary as the claude process. The helper writes its
// arguments, working directory and stdin to recordDir and then plays the given behaviour.
func fakeClaude(t *testing.T, behaviour string) (recordDir string) {
	t.Helper()
	recordDir = t.TempDir()
	origLook, origCommand := lookPath, command
	t.Cleanup(func() { lookPath, command = origLook, origCommand })
	lookPath = func(string) (string, error) { return "/fake/claude", nil }
	command = func(ctx context.Context, name string, args ...string) *exec.Cmd {
		helperArgs := append([]string{"-test.run=TestHelperProcess", "--", name}, args...)
		cmd := exec.CommandContext(ctx, os.Args[0], helperArgs...)
		cmd.Env = append(os.Environ(),
			"OBA_FAKE_CLAUDE="+behaviour,
			"OBA_FAKE_RECORD="+recordDir,
			"OBA_FAKE_FIXTURE="+mustAbs(t, fixture))
		return cmd
	}
	return recordDir
}

func mustAbs(t *testing.T, path string) string {
	t.Helper()
	abs, err := filepath.Abs(path)
	if err != nil {
		t.Fatal(err)
	}
	return abs
}

// TestHelperProcess is the fake claude; it does nothing unless started by fakeClaude.
func TestHelperProcess(t *testing.T) {
	behaviour := os.Getenv("OBA_FAKE_CLAUDE")
	if behaviour == "" {
		return
	}
	os.Exit(runFakeClaude(behaviour))
}

func runFakeClaude(behaviour string) int {
	record := os.Getenv("OBA_FAKE_RECORD")
	args := os.Args
	for i, a := range args {
		if a == "--" {
			args = args[i+2:]
			break
		}
	}
	stdin, err := io.ReadAll(os.Stdin)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 9
	}
	wd, err := os.Getwd()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 9
	}
	entries, err := os.ReadDir(wd)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 9
	}
	rec, err := json.Marshal(map[string]any{"args": args, "stdin": string(stdin), "dir": wd, "entries": len(entries)})
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 9
	}
	if err := os.WriteFile(filepath.Join(record, "record.json"), rec, 0o600); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 9
	}
	switch behaviour {
	case "fixture":
		data, err := os.ReadFile(os.Getenv("OBA_FAKE_FIXTURE"))
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			return 9
		}
		_, _ = os.Stdout.Write(data)
		return 0
	case "exit":
		fmt.Fprintln(os.Stderr, "line one\nInvalid API key - please run /login")
		return 1
	case "is-error":
		fmt.Println(`{"type":"result","subtype":"error_during_execution","is_error":true,"result":"boom"}`)
		fmt.Fprintln(os.Stderr, "stderr detail")
		return 1
	}
	return 9
}

type record struct {
	Args    []string `json:"args"`
	Stdin   string   `json:"stdin"`
	Dir     string   `json:"dir"`
	Entries int      `json:"entries"`
}

func readRecord(t *testing.T, dir string) record {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(dir, "record.json"))
	if err != nil {
		t.Fatal(err)
	}
	var r record
	if err := json.Unmarshal(data, &r); err != nil {
		t.Fatal(err)
	}
	return r
}

func TestRunPassesBriefOnStdinInEmptyTempDir(t *testing.T) {
	recordDir := fakeClaude(t, "fixture")
	var progress []string
	raw, err := Run(context.Background(), "the brief", `{"s":1}`, collect(&progress))
	if err != nil {
		t.Fatal(err)
	}
	if got := decodeCandidates(t, raw); len(got) != 2 || len(progress) != 2 {
		t.Errorf("candidates %d, progress %d", len(got), len(progress))
	}
	r := readRecord(t, recordDir)
	if !reflect.DeepEqual(r.Args, Args(`{"s":1}`)) {
		t.Errorf("args = %q", r.Args)
	}
	if r.Stdin != "the brief" {
		t.Errorf("stdin = %q", r.Stdin)
	}
	if r.Entries != 0 || !strings.Contains(filepath.Base(r.Dir), "oba-claude-") {
		t.Errorf("working directory %s had %d entries", r.Dir, r.Entries)
	}
	if _, err := os.Stat(r.Dir); !os.IsNotExist(err) {
		t.Errorf("working directory %s was not removed: %v", r.Dir, err)
	}
}

func TestRunNonZeroExitCarriesStderr(t *testing.T) {
	fakeClaude(t, "exit")
	_, err := Run(context.Background(), "b", "{}", nil)
	if err == nil || !strings.Contains(err.Error(), "Invalid API key - please run /login") {
		t.Fatalf("err = %v", err)
	}
}

func TestRunIsErrorCarriesStderr(t *testing.T) {
	fakeClaude(t, "is-error")
	_, err := Run(context.Background(), "b", "{}", nil)
	var resultErr *ResultError
	if !errors.As(err, &resultErr) {
		t.Fatalf("err = %v, want *ResultError", err)
	}
	if resultErr.Message != "boom" || resultErr.Stderr != "stderr detail" {
		t.Errorf("result error = %+v", resultErr)
	}
}

func TestRunCancelled(t *testing.T) {
	fakeClaude(t, "fixture")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := Run(ctx, "b", "{}", nil)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("err = %v, want context.Canceled", err)
	}
}

func TestRunNotInstalled(t *testing.T) {
	origLook := lookPath
	t.Cleanup(func() { lookPath = origLook })
	lookPath = func(string) (string, error) { return "", exec.ErrNotFound }
	if _, err := Run(context.Background(), "b", "{}", nil); !errors.Is(err, ErrNotInstalled) {
		t.Errorf("Run err = %v", err)
	}
	if err := Installed(); !errors.Is(err, ErrNotInstalled) {
		t.Errorf("Installed err = %v", err)
	}
}
