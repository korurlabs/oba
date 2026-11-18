// Package claude runs Claude Code headless on the user's machine with web search and page fetch,
// and reads its stream-json output: one progress line per web search or page fetch, then the
// JSON of the final result. The caller gives the brief and the JSON Schema of that result and
// decodes it; this package knows nothing of what is searched for. Every string Claude Code
// returns, in the result, the progress lines and an error message, passes through
// guardrail first, because it is built from web pages and is printed to a terminal.
//
// Claude Code gets web search and page fetch only. It runs with --restricted (no command tools,
// no user, project or local settings), --strict-mcp-config without a config (no MCP server, so no
// oba access), in an empty temporary working directory, and reads the brief from stdin so that
// the brief never shows in ps. The CLI, not Claude Code, writes to Otak.
package claude

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Binary is the Claude Code executable looked up on PATH.
const Binary = "claude"

const (
	toolWebSearch = "WebSearch"
	toolWebFetch  = "WebFetch"
	webTools      = toolWebSearch + "," + toolWebFetch

	eventAssistant = "assistant"
	eventResult    = "result"
	blockToolUse   = "tool_use"

	// waitDelay bounds how long Wait waits for the output pipes after the process was stopped.
	waitDelay = 5 * time.Second
	// stderrLines is how many trailing stderr lines an error carries.
	stderrLines = 10
	// stderrKeepBytes caps the stderr kept in memory; only its tail is reported.
	stderrKeepBytes = 64 * 1024
)

// ErrNotInstalled means the claude executable is not on PATH. It is printed
// as it is, so it keeps the full stop.
var ErrNotInstalled = errors.New("this command needs Claude Code; install it from https://claude.com/claude-code and run 'claude' once to sign in.") //nolint:staticcheck // ST1005: shown to the user verbatim

// ProgressFunc receives one human-readable line per web search or page fetch.
type ProgressFunc func(line string)

// ResultError is a final result that Claude Code itself marked as an error.
type ResultError struct {
	Subtype string
	Message string
	Stderr  string
}

func (e *ResultError) Error() string {
	msg := fmt.Sprintf("Claude Code failed (%s)", e.Subtype)
	if e.Message != "" {
		msg += ": " + e.Message
	}
	if e.Stderr != "" {
		msg += "\n" + e.Stderr
	}
	return msg
}

// lookPath and command are replaced in tests.
var (
	lookPath = exec.LookPath
	command  = exec.CommandContext
)

// Installed reports ErrNotInstalled when claude is not on PATH.
func Installed() error {
	if _, err := lookPath(Binary); err != nil {
		return ErrNotInstalled
	}
	return nil
}

// Args is the argument list claude runs with; schema is the JSON Schema of the final output.
func Args(schema string) []string {
	return []string{
		"-p",
		"--restricted",
		"--tools", webTools,
		"--allowedTools", webTools,
		"--strict-mcp-config",
		"--disable-slash-commands",
		"--no-session-persistence",
		"--permission-mode", "dontAsk",
		"--output-format", "stream-json",
		"--verbose",
		"--json-schema", schema,
	}
}

// Run starts Claude Code with the brief on stdin in a fresh empty temporary directory, reports
// progress through onProgress (nil for none) and returns the JSON of the final result, cleaned
// of terminal controls (see Parse).
// Cancelling ctx stops Claude Code; Run then returns the context's error.
func Run(ctx context.Context, brief, schema string, onProgress ProgressFunc) (result json.RawMessage, err error) {
	path, lookErr := lookPath(Binary)
	if lookErr != nil {
		return nil, ErrNotInstalled
	}

	dir, err := os.MkdirTemp("", "oba-claude-")
	if err != nil {
		return nil, fmt.Errorf("failed to create a working directory for Claude Code: %w", err)
	}
	defer func() {
		if rmErr := os.RemoveAll(dir); rmErr != nil {
			err = errors.Join(err, fmt.Errorf("failed to remove the working directory %s: %w", dir, rmErr))
		}
	}()

	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	cmd := command(runCtx, path, Args(schema)...)
	cmd.Dir = dir
	cmd.Stdin = strings.NewReader(brief)
	stderr := &tailBuffer{max: stderrKeepBytes}
	cmd.Stderr = stderr
	cmd.WaitDelay = waitDelay
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to read the output of Claude Code: %w", err)
	}
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("failed to start Claude Code: %w", err)
	}

	result, parseErr := Parse(stdout, onProgress)
	if parseErr != nil {
		// Keep reading so that Claude Code is not blocked on a full pipe and ends with its own exit
		// status, which explains the failure better than the unreadable output does.
		if _, drainErr := io.Copy(io.Discard, stdout); drainErr != nil {
			parseErr = errors.Join(parseErr, fmt.Errorf("failed to read the rest of the Claude Code output: %w", drainErr))
		}
	}
	waitErr := cmd.Wait()

	if ctxErr := ctx.Err(); ctxErr != nil {
		return nil, fmt.Errorf("stopped Claude Code: %w", ctxErr)
	}
	var resultErr *ResultError
	if errors.As(parseErr, &resultErr) {
		resultErr.Stderr = stderr.lastLines(stderrLines)
		return nil, resultErr
	}
	if waitErr != nil {
		return nil, withStderr(fmt.Errorf("claude exited with an error: %w", waitErr), stderr)
	}
	if parseErr != nil {
		return nil, withStderr(parseErr, stderr)
	}
	return result, nil
}

// event is the part of a stream-json line the parser needs.
type event struct {
	Type    string `json:"type"`
	Subtype string `json:"subtype"`
	Message *struct {
		Content []struct {
			Type  string          `json:"type"`
			Name  string          `json:"name"`
			Input json.RawMessage `json:"input"`
		} `json:"content"`
	} `json:"message"`
	IsError          bool            `json:"is_error"`
	Result           json.RawMessage `json:"result"`
	StructuredOutput json.RawMessage `json:"structured_output"`
	Errors           []string        `json:"errors"`
}

// Parse reads Claude Code's stream-json output (one JSON object per line), reports each web
// search and page fetch through onProgress and returns the JSON of the result event: its
// structured_output, else its text result, which must be JSON, with any Markdown code fence
// removed. Its strings and keys are cleaned of terminal controls by guardrail.CleanJSON. A
// result marked is_error returns a *ResultError. It reads r to the end.
func Parse(r io.Reader, onProgress ProgressFunc) (json.RawMessage, error) {
	reader := bufio.NewReader(r)
	var final *event
	lineNo := 0
	for {
		line, readErr := reader.ReadBytes('\n')
		if len(bytes.TrimSpace(line)) > 0 {
			lineNo++
			var ev event
			if err := json.Unmarshal(line, &ev); err != nil {
				return nil, fmt.Errorf("line %d of the Claude Code output is not JSON: %w", lineNo, err)
			}
			switch ev.Type {
			case eventAssistant:
				reportProgress(ev, onProgress)
			case eventResult:
				final = &ev
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return nil, fmt.Errorf("failed to read the output of Claude Code: %w", readErr)
		}
	}
	if final == nil {
		return nil, errors.New("the Claude Code output ended without a result")
	}
	result, err := resultOf(final)
	if err != nil {
		return nil, err
	}
	clean, err := guardrail.CleanJSON(result)
	if err != nil {
		return nil, fmt.Errorf("failed to clean the result of Claude Code: %w", err)
	}
	return clean, nil
}

func reportProgress(ev event, onProgress ProgressFunc) {
	if onProgress == nil || ev.Message == nil {
		return
	}
	for _, block := range ev.Message.Content {
		if block.Type != blockToolUse {
			continue
		}
		var input struct {
			Query string `json:"query"`
			URL   string `json:"url"`
		}
		if err := json.Unmarshal(block.Input, &input); err != nil {
			// A tool input of another shape only loses its progress line; the result is unaffected.
			onProgress(guardrail.Sanitize(fmt.Sprintf("%s: (input not readable: %v)", block.Name, err)))
			continue
		}
		switch block.Name {
		case toolWebSearch:
			onProgress("Searching: " + guardrail.Sanitize(input.Query))
		case toolWebFetch:
			onProgress("Reading: " + guardrail.Sanitize(input.URL))
		}
	}
}

func resultOf(final *event) (json.RawMessage, error) {
	if final.IsError {
		return nil, &ResultError{Subtype: guardrail.Sanitize(final.Subtype), Message: guardrail.Sanitize(resultMessage(final))}
	}
	if len(final.StructuredOutput) > 0 && string(final.StructuredOutput) != "null" {
		return final.StructuredOutput, nil
	}
	var text string
	if err := json.Unmarshal(final.Result, &text); err != nil {
		return nil, errors.New("the Claude Code result has neither structured output nor a text result")
	}
	result := []byte(stripFence(text))
	if !json.Valid(result) {
		return nil, errors.New("the result of Claude Code is not JSON")
	}
	return result, nil
}

func resultMessage(final *event) string {
	var text string
	if err := json.Unmarshal(final.Result, &text); err == nil && text != "" {
		return text
	}
	return strings.Join(final.Errors, "; ")
}

// stripFence removes a Markdown code fence around a JSON text result.
func stripFence(text string) string {
	t := strings.TrimSpace(text)
	if !strings.HasPrefix(t, "```") {
		return t
	}
	t = strings.TrimPrefix(t, "```")
	if nl := strings.IndexByte(t, '\n'); nl >= 0 {
		t = t[nl+1:]
	}
	return strings.TrimSpace(strings.TrimSuffix(strings.TrimSpace(t), "```"))
}

func withStderr(err error, stderr *tailBuffer) error {
	tail := stderr.lastLines(stderrLines)
	if tail == "" {
		return err
	}
	return fmt.Errorf("%w\n%s", err, tail)
}

// tailBuffer keeps the last max bytes written to it.
type tailBuffer struct {
	max int
	buf []byte
}

func (t *tailBuffer) Write(p []byte) (int, error) {
	t.buf = append(t.buf, p...)
	if over := len(t.buf) - t.max; over > 0 {
		t.buf = t.buf[over:]
	}
	return len(p), nil
}

func (t *tailBuffer) lastLines(n int) string {
	lines := strings.Split(strings.TrimRight(string(t.buf), "\n"), "\n")
	if len(lines) > n {
		lines = lines[len(lines)-n:]
	}
	return guardrail.Sanitize(strings.TrimSpace(strings.Join(lines, "\n")))
}
