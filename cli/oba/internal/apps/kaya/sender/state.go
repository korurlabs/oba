package sender

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
)

// Files of the state directory and of a session's queue directory that only the sender writes,
// beside the ones the capture names (see the capture package).
const (
	lockFile          = "sender.lock"
	cursorFile        = "cursor.json"
	configReleaseFile = "config-release.json"
	invalidLinesFile  = "events.invalid.jsonl"
	isoSeconds        = "2006-01-02T15:04:05Z"
)

// Token states the sender reports in state.json; the hook's token warning reads them.
const (
	tokenOK      = "ok"
	tokenMissing = "missing"
	tokenRefused = "refused"
)

func iso(t time.Time) string { return t.UTC().Format(isoSeconds) }

// fingerprint is a stable, one-way label of the token. It tells a new token from one Kaya
// refused, so the token itself never has to be written to disk.
func fingerprint(token string) string {
	sum := sha256.Sum256([]byte("kaya-recorder-fingerprint:" + token))
	return hex.EncodeToString(sum[:])[:16]
}

// parked marks a session whose batch Kaya refused for its content. It is skipped until the token
// changes, because a new token is the developer's sign that something was fixed. A park after a
// machine name refusal is the one exception, see codeMachineName. ObaVersion is the version of
// the oba that parked the session; a park written by an oba that did not keep it has none.
type parked struct {
	Status           int     `json:"status"`
	Code             *string `json:"code"`
	At               string  `json:"at"`
	TokenFingerprint string  `json:"tokenFingerprint"`
	ObaVersion       string  `json:"obaVersion,omitempty"`
}

// cursor is what Kaya acknowledged of a session: the highest seq it holds and the modification
// time, in nanoseconds, of each handoff and plan snapshot it holds.
type cursor struct {
	AcknowledgedSeq int64            `json:"acknowledgedSeq"`
	Handoffs        map[string]int64 `json:"handoffs"`
	Plans           map[string]int64 `json:"plans"`
	SentAt          string           `json:"sentAt,omitempty"`
	Parked          *parked          `json:"parked,omitempty"`
}

// readCursor is the session's cursor. A damaged cursor starts the session over: Kaya stores each
// seq once, so sending it again costs bandwidth and loses nothing.
func (s *run) readCursor(dir string) *cursor {
	c := &cursor{}
	if _, err := readJSON(filepath.Join(dir, cursorFile), c); err != nil {
		s.log("%s/%s unreadable, sending the session again: %v", dir, cursorFile, err)
		c = &cursor{}
	}
	if c.Handoffs == nil {
		c.Handoffs = map[string]int64{}
	}
	if c.Plans == nil {
		c.Plans = map[string]int64{}
	}
	return c
}

func writeCursor(dir string, c *cursor) error {
	return writeJSON(filepath.Join(dir, cursorFile), c)
}

// readState is state.json as a map, so keys of another version survive a rewrite. A damaged
// file starts over: it only caches what the sender learns again on its next request.
func (s *run) readState() map[string]any {
	state := map[string]any{}
	found, err := readJSON(filepath.Join(s.stateDir, capture.StateFile), &state)
	if err != nil || !found || state == nil {
		if err != nil {
			s.log("%s unreadable, starting over: %v", capture.StateFile, err)
		}
		return map[string]any{}
	}
	return state
}

// writeState merges changes into state.json.
func (s *run) writeState(changes map[string]any) error {
	state := s.readState()
	for key, value := range changes {
		state[key] = value
	}
	state["updatedAt"] = iso(s.now())
	return writeJSON(filepath.Join(s.stateDir, capture.StateFile), state)
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

// readJSON decodes path into target, numbers kept as written. found is false when the file does
// not exist; a file that is not JSON is an error, so the caller decides what it means.
func readJSON(path string, target any) (found bool, err error) {
	data, err := os.ReadFile(path) // #nosec G304 -- a file of the sender's state directory
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

// decodeNumbers decodes one JSON value and keeps numbers as written, so a seq is never rounded
// through a float. Trailing data is an error.
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

// encodeCompact is value as one JSON text without HTML escaping, as Kaya receives it.
func encodeCompact(value any) ([]byte, error) {
	var buf bytes.Buffer
	encoder := json.NewEncoder(&buf)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(value); err != nil {
		return nil, err
	}
	return bytes.TrimSuffix(buf.Bytes(), []byte("\n")), nil
}

// removeFile removes path; a file already gone is the state the caller wants.
func removeFile(path string) error {
	if err := os.Remove(path); err != nil && !errors.Is(err, fs.ErrNotExist) {
		return err
	}
	return nil
}
