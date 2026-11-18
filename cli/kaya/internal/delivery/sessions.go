package delivery

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"syscall"
	"time"
)

// sessionID is the shape of a Claude Code session id the server accepts; anything else never
// becomes a file name.
var sessionID = regexp.MustCompile(`^[A-Za-z0-9_-]{1,100}$`)

// ValidSessionID reports whether id can name a session file.
func ValidSessionID(id string) bool {
	return sessionID.MatchString(id)
}

// Session is a live Claude Code process of the checkout. The launcher writes one per process
// before it execs Claude Code (pid-<pid>.json); the SessionStart hook writes one per session id.
// Both name the Claude Code process, so a pid counts once.
type Session struct {
	PID         int    `json:"pid"`
	SessionID   string `json:"sessionId,omitempty"`
	Release     int    `json:"release,omitempty"`
	State       string `json:"state,omitempty"`
	DeliveredBy string `json:"deliveredBy,omitempty"`
	StartedAt   string `json:"startedAt"`
}

// RegisterSession records a live session. Without a session id it is the launcher's record of
// its process.
func (c *Checkout) RegisterSession(s Session) error {
	if err := c.ensureKayaDir(); err != nil {
		return err
	}
	name := fmt.Sprintf("pid-%d.json", s.PID)
	if s.SessionID != "" {
		if !ValidSessionID(s.SessionID) {
			return fmt.Errorf("session id %q is not valid", s.SessionID)
		}
		name = s.SessionID + ".json"
	}
	if s.StartedAt == "" {
		s.StartedAt = time.Now().UTC().Format(time.RFC3339)
	}
	data, err := json.Marshal(s)
	if err != nil {
		return fmt.Errorf("encode the session record: %w", err)
	}
	return writeFileAtomic(filepath.Join(c.sessionsDir(), name), data, 0o600)
}

// RemoveSession drops the record of a session that ended.
func (c *Checkout) RemoveSession(id string) error {
	if !ValidSessionID(id) {
		return fmt.Errorf("session id %q is not valid", id)
	}
	err := os.Remove(filepath.Join(c.sessionsDir(), id+".json"))
	if err != nil && !errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("remove the session record: %w", err)
	}
	return nil
}

// LiveSessions lists the sessions whose process still runs, one per process, leaving out the
// process excludePID (the caller's own Claude Code). Records of ended processes are removed.
func (c *Checkout) LiveSessions(excludePID int) ([]Session, error) {
	entries, err := os.ReadDir(c.sessionsDir())
	if errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", c.sessionsDir(), err)
	}
	byPID := map[int]Session{}
	for _, entry := range entries {
		if !strings.HasSuffix(entry.Name(), ".json") || strings.HasPrefix(entry.Name(), ".") {
			continue
		}
		path := filepath.Join(c.sessionsDir(), entry.Name())
		data, err := os.ReadFile(path) // #nosec G304 -- a file of the checkout's own .kaya/sessions
		if err != nil {
			return nil, fmt.Errorf("read %s: %w", path, err)
		}
		var s Session
		if err := json.Unmarshal(data, &s); err != nil || s.PID <= 0 {
			// A damaged record names no process to wait for; dropping it keeps a broken file from
			// pinning the checkout forever.
			_ = os.Remove(path)
			continue
		}
		if !processAlive(s.PID) {
			_ = os.Remove(path)
			continue
		}
		if s.PID == excludePID {
			continue
		}
		if known, ok := byPID[s.PID]; !ok || known.SessionID == "" {
			byPID[s.PID] = s
		}
	}
	sessions := make([]Session, 0, len(byPID))
	for _, s := range byPID {
		sessions = append(sessions, s)
	}
	sort.Slice(sessions, func(i, j int) bool { return sessions[i].PID < sessions[j].PID })
	return sessions, nil
}

// processAlive reports whether pid runs; a process of another user still counts.
func processAlive(pid int) bool {
	err := syscall.Kill(pid, 0)
	return err == nil || errors.Is(err, syscall.EPERM)
}
