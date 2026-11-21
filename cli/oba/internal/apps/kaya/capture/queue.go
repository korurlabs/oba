package capture

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

// The queue under the state directory ($XDG_STATE_HOME/kaya, see delivery.RecorderStateDir):
//
//	queue/sessions/<id>/  events.jsonl, summary.json, session.json, config-release.json,
//	                      cursor.json (the sender's), handoffs/, plans/, .lock, .token-warned
//	queue/usage/<id>.json the status line's usage file
//	state.json            what the sender last learned about its token, never the token
//	recorder.log          the sender's and the status line's log
//
// The format belongs to the kaya CLI alone. Every directory is mode 0700: the queue holds
// prompts and commands.
const (
	EventsFile  = "events.jsonl"
	SummaryFile = "summary.json"
	SessionFile = "session.json"
	StateFile   = "state.json"
	LogFile     = "recorder.log"
	lockFile    = ".lock"
	warnedFile  = ".token-warned"

	// logMaxBytes is the size at which recorder.log moves to recorder.log.1, so the two files
	// together stay below twice this size.
	logMaxBytes = 1_000_000
	// lastSeqTail is how much of events.jsonl the next seq is read from, so a long session
	// costs a hook no more than a short one.
	lastSeqTail = 65536
)

// SessionsDir is queue/sessions of the state directory.
func SessionsDir(stateDir string) string {
	return filepath.Join(stateDir, "queue", "sessions")
}

// SessionDir is the queue directory of one session.
func SessionDir(stateDir, sessionID string) string {
	return filepath.Join(SessionsDir(stateDir), sessionID)
}

// EnsureStateDir creates the state directory readable by its owner only and returns its path.
func EnsureStateDir() (string, error) {
	dir, err := delivery.RecorderStateDir()
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", fmt.Errorf("create %s: %w", dir, err)
	}
	info, err := os.Stat(dir)
	if err != nil {
		return "", fmt.Errorf("inspect %s: %w", dir, err)
	}
	if info.Mode().Perm()&0o077 != 0 {
		if err := os.Chmod(dir, 0o700); err != nil {
			return "", fmt.Errorf("restrict %s to its owner: %w", dir, err)
		}
	}
	return dir, nil
}

// LockSession takes the exclusive lock of a session's queue directory, which every append and
// every rewrite of events.jsonl holds, and returns its release.
func LockSession(dir string) (func(), error) {
	path := filepath.Join(dir, lockFile)
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed name in the session's queue directory
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	if err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX); err != nil { // #nosec G115 -- a file descriptor fits an int
		_ = file.Close()
		return nil, fmt.Errorf("lock %s: %w", path, err)
	}
	return func() {
		_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN) // #nosec G115 -- a file descriptor fits an int
		_ = file.Close()
	}, nil
}

// appendEvent adds one line to events.jsonl in a single write, so parallel subagents of a
// session never interleave their lines.
func appendEvent(dir string, event map[string]any) error {
	line, err := encodeCompact(event)
	if err != nil {
		return fmt.Errorf("encode the event: %w", err)
	}
	path := filepath.Join(dir, EventsFile)
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0o600) // #nosec G304 -- fixed name in the session's queue directory
	if err != nil {
		return fmt.Errorf("open %s: %w", path, err)
	}
	_, writeErr := file.Write(append(line, '\n'))
	closeErr := file.Close()
	if err := errors.Join(writeErr, closeErr); err != nil {
		return fmt.Errorf("append to %s: %w", path, err)
	}
	return nil
}

// lastSeq is the seq of the last event in events.jsonl, 0 when it holds none. Lines that are no
// event (a damaged line, a half-written one) are passed over.
func lastSeq(path string) (int64, error) {
	file, err := os.Open(path) // #nosec G304 -- fixed name in the session's queue directory
	if errors.Is(err, fs.ErrNotExist) {
		return 0, nil
	}
	if err != nil {
		return 0, fmt.Errorf("open %s: %w", path, err)
	}
	defer func() { _ = file.Close() }()
	info, err := file.Stat()
	if err != nil {
		return 0, fmt.Errorf("inspect %s: %w", path, err)
	}
	start := max(info.Size()-lastSeqTail, 0)
	data := make([]byte, info.Size()-start)
	if _, err := file.ReadAt(data, start); err != nil {
		return 0, fmt.Errorf("read %s: %w", path, err)
	}
	lines := bytes.Split(data, []byte("\n"))
	for i := len(lines) - 1; i >= 0; i-- {
		if seq, ok := lineSeq(lines[i]); ok {
			return seq, nil
		}
	}
	return 0, nil
}

// lineSeq is the integer seq of one queue line. A float or a boolean is no seq.
func lineSeq(line []byte) (int64, bool) {
	var event map[string]any
	if err := decodeNumbers(line, &event); err != nil || event == nil {
		return 0, false
	}
	number, ok := event["seq"].(json.Number)
	if !ok {
		return 0, false
	}
	seq, err := strconv.ParseInt(number.String(), 10, 64)
	return seq, err == nil
}

// writeAtomic writes data through a temporary file and a rename, so a reader never sees half a
// file. The temporary name holds ".tmp-", which every reader of the queue skips.
func writeAtomic(path string, data []byte) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create %s: %w", dir, err)
	}
	tmp, err := os.CreateTemp(dir, filepath.Base(path)+".tmp-*")
	if err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	name := tmp.Name()
	_, writeErr := tmp.Write(data)
	closeErr := tmp.Close()
	if err := errors.Join(writeErr, closeErr); err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("write %s: %w", path, err)
	}
	if err := os.Rename(name, path); err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}

// writeJSON writes value indented, as the queue's JSON files are kept.
func writeJSON(path string, value any) error {
	var buf bytes.Buffer
	encoder := json.NewEncoder(&buf)
	encoder.SetEscapeHTML(false)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(value); err != nil {
		return fmt.Errorf("encode %s: %w", path, err)
	}
	return writeAtomic(path, buf.Bytes())
}

// readJSON decodes path into target. found is false when the file does not exist; a file that
// is not JSON is an error, so the caller decides what a damaged file means.
func readJSON(path string, target any) (found bool, err error) {
	data, err := os.ReadFile(path) // #nosec G304 G703 -- a file of the queue or of Claude Code's config
	if errors.Is(err, fs.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("read %s: %w", path, err)
	}
	if err := decodeNumbers(data, target); err != nil {
		return true, fmt.Errorf("%s is not JSON: %w", path, err)
	}
	return true, nil
}

// encodeCompact is one JSON line without HTML escaping and without its newline.
func encodeCompact(value any) ([]byte, error) {
	var buf bytes.Buffer
	encoder := json.NewEncoder(&buf)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(value); err != nil {
		return nil, err
	}
	return bytes.TrimSuffix(buf.Bytes(), []byte("\n")), nil
}

// decodeNumbers decodes one JSON value and keeps numbers as written, so an id or a seq is never
// rounded through a float. Trailing data is an error.
func decodeNumbers(data []byte, target any) error {
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if _, err := decoder.Token(); !errors.Is(err, io.EOF) {
		return errors.New("unexpected data after the JSON value")
	}
	return nil
}

// Log appends one line to recorder.log, moving a full log to recorder.log.1 first. A line that
// cannot be written is returned as an error so the caller can show it instead.
func Log(message string) error {
	dir, err := EnsureStateDir()
	if err != nil {
		return err
	}
	path := filepath.Join(dir, LogFile)
	if info, err := os.Stat(path); err == nil && info.Size() >= logMaxBytes {
		if err := os.Rename(path, path+".1"); err != nil {
			return fmt.Errorf("rotate %s: %w", path, err)
		}
	}
	line := fmt.Sprintf("%s %d %s\n", time.Now().UTC().Format(isoSeconds), os.Getpid(), strings.ToValidUTF8(message, "�"))
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0o600) // #nosec G304 -- fixed name in the state directory
	if err != nil {
		return fmt.Errorf("open %s: %w", path, err)
	}
	_, writeErr := file.WriteString(line)
	closeErr := file.Close()
	if err := errors.Join(writeErr, closeErr); err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}
