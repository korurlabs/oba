// Package sender sends the session queue the capture writes (see the capture package) to Kaya,
// POST /kaya/ingest/batches, as the developer's machine token.
//
// Every two seconds it sends, per session, the events after the session's acknowledged seq (at
// most 500), the handoff and plan snapshots changed since Kaya last acknowledged them and the
// session's usage file, and keeps what Kaya acknowledged in the session's cursor.json, so it
// replays in order after an outage; Kaya stores each seq once. A 403 KAY_SEC_007 (no user holds
// the session's git email yet), a 429, a 5xx and a network error back off from 2 s to 5 min and
// keep the session queued. A 401 stops the sender until the token changes. A session whose batch
// Kaya refuses for its content (400, 409, 413, 422) is parked until the token changes; one parked
// after the machine name refusal of an older Kaya release (KAY_VAL_005) is also sent once more by
// another oba version. One sender runs per machine: it holds sender.lock. It exits after ten idle
// minutes.
//
// The token is only ever put in the Authorization header: it is never logged, never written to
// disk, and never sent over plain http except to a loopback address. state.json keeps a one-way
// fingerprint of it.
package sender

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	machinetoken "github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
)

const (
	sendInterval = 2 * time.Second
	backoffMin   = 2 * time.Second
	backoffMax   = 5 * time.Minute
	idleExit     = 10 * time.Minute
	pruneAfter   = 7 * 24 * time.Hour
	// batchesPerSessionRound keeps one long session from starving the others.
	batchesPerSessionRound = 20
	// restartWait and restartWaitMax bound how often a hook starts a sender: each start since a
	// sender last ran doubles the wait, so a sender that exits at once (no token) is not started
	// again on every tool call.
	restartWait    = 30 * time.Second
	restartWaitMax = 30 * time.Minute

	tokenEnv       = "KAYA_RECORDER_TOKEN"
	senderEnv      = "KAYA_SENDER"
	codeUnknownGit = "KAY_SEC_007"
	// codeMachineName is the refusal of older Kaya releases for a batch whose machine name differs
	// from the name of the token's machine. The name is the host name the hook recorded, which
	// macOS changes with the network, so a new token would not have fixed it; later Kaya releases
	// take the name as a label. A session parked on this code is therefore sent once more by each
	// oba version other than the one that parked it, and a second refusal parks it again under the
	// running version, so it is never retried in a loop.
	codeMachineName = "KAY_VAL_005"
)

var (
	tokenPattern = regexp.MustCompile(`^kyt_[A-Za-z0-9_-]{43}$`)
	// parkStatuses are the answers that refuse a batch for its content: sending it again cannot
	// succeed until something changes, which a new token stands for.
	parkStatuses = map[int]bool{400: true, 409: true, 413: true, 422: true}
)

// Poster sends one encoded batch; *client.Client is one.
type Poster interface {
	PostIngestBatch(ctx context.Context, body []byte) (json.RawMessage, error)
}

// Config is what one sender run needs. Only APIURL and Token are required; the rest replace the
// machine in tests.
type Config struct {
	APIURL string
	// Token reads the machine token once per run.
	Token func(ctx context.Context) (string, error)
	// Once sends one round and returns.
	Once bool
	// Version is the running oba's version, which a park records. Without it no park after a
	// machine name refusal is sent once more, since the sender cannot tell whether it already
	// tried.
	Version string

	Client   func(apiURL, token string) Poster
	Resolver *seed.RemoteResolver
	Sleep    func(ctx context.Context, d time.Duration) error
	Now      func() time.Time
	Hostname func() (string, error)
}

// stopError ends the run: Kaya refused the token.
type stopError struct{ reason string }

func (e *stopError) Error() string { return e.reason }

// retryError ends the round: Kaya cannot take the batch now and the sender backs off.
type retryError struct{ reason string }

func (e *retryError) Error() string { return e.reason }

// run is one sender process.
type run struct {
	stateDir string
	token    string
	tokenFP  string
	version  string
	client   Poster
	resolver *seed.RemoteResolver
	now      func() time.Time
	hostname func() (string, error)
	reported map[string]bool
	// unresolvedHosts are the ssh hosts the resolver could not resolve in this run.
	unresolvedHosts map[string]bool
}

// log appends a line to recorder.log; a line the log cannot take goes to stderr, which a
// detached sender also points at the log, so the reason is never lost.
func (s *run) log(format string, args ...any) {
	message := fmt.Sprintf(format, args...)
	if err := capture.Log(message); err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "oba kaya sender: %v: %s\n", err, message)
	}
}

// reportOnce logs a reason for a session once per run, so a session that cannot be sent is not
// logged every round.
func (s *run) reportOnce(sessionID, key, message string) {
	if s.reported[sessionID+"\x00"+key] {
		return
	}
	s.reported[sessionID+"\x00"+key] = true
	s.log("%s: %s", sessionID, message)
}

// Run is `oba kaya sender`. It returns nil when another sender holds sender.lock, when there is no
// usable token or URL (the reason is logged and in state.json), after its idle time and on a
// refused token; an error means the sender could not run at all.
func Run(ctx context.Context, cfg Config) error {
	stateDir, err := capture.EnsureStateDir()
	if err != nil {
		return err
	}
	lockPath := filepath.Join(stateDir, lockFile)
	lock, err := os.OpenFile(lockPath, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed name in the state directory
	if err != nil {
		return fmt.Errorf("open %s: %w", lockPath, err)
	}
	defer func() { _ = lock.Close() }()
	if err := syscall.Flock(int(lock.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil { // #nosec G115 -- a file descriptor fits an int
		if errors.Is(err, syscall.EWOULDBLOCK) {
			return nil
		}
		return fmt.Errorf("lock %s: %w", lockPath, err)
	}
	// A sender that runs resets the hooks' start back-off.
	if err := writeAtomic(filepath.Join(stateDir, capture.SenderStartedFile), []byte("0\n")); err != nil {
		return err
	}

	s := &run{stateDir: stateDir, version: cfg.Version, now: cfg.Now, hostname: cfg.Hostname, resolver: cfg.Resolver, reported: map[string]bool{}, unresolvedHosts: map[string]bool{}}
	if s.now == nil {
		s.now = time.Now
	}
	if s.hostname == nil {
		s.hostname = os.Hostname
	}
	if s.resolver == nil {
		s.resolver = seed.NewRemoteResolver()
	}
	sleep := cfg.Sleep
	if sleep == nil {
		sleep = sleepContext
	}

	token, tokenErr := cfg.Token(ctx)
	if tokenErr != nil || !tokenPattern.MatchString(token) {
		reason := "it does not have the form kyt_ and 43 characters"
		if tokenErr != nil {
			reason = tokenErr.Error()
		}
		s.log("no valid %s (%s); the queue is kept until one is set", tokenEnv, reason)
		return s.writeState(map[string]any{"token": tokenMissing})
	}
	s.token, s.tokenFP = token, fingerprint(token)
	if refused, _ := s.readState()["refusedFingerprint"].(string); refused == s.tokenFP {
		s.log("%s was refused before; set a new token", tokenEnv)
		return nil
	}
	if err := machinetoken.CheckTransport(cfg.APIURL); err != nil {
		s.log("%v", err)
		return nil
	}
	if cfg.Client != nil {
		s.client = cfg.Client(cfg.APIURL, token)
	} else {
		s.client = client.NewIngest(cfg.APIURL, func(context.Context) (string, error) { return token, nil })
	}
	return s.loop(ctx, cfg.Once, sleep)
}

func (s *run) loop(ctx context.Context, once bool, sleep func(context.Context, time.Duration) error) error {
	backoff := backoffMin
	lastSent := s.now()
	for {
		sent, err := s.sendRound(ctx)
		var stop *stopError
		var retry *retryError
		switch {
		case errors.As(err, &stop):
			s.log("stopped: %s; set a new token", stop.reason)
			return s.writeState(map[string]any{"token": tokenRefused, "refusedFingerprint": s.tokenFP})
		case errors.As(err, &retry):
			s.log("%s; retrying in %d s", retry.reason, int(backoff.Seconds()))
			if once {
				return nil
			}
			if err := sleep(ctx, backoff); err != nil {
				return err
			}
			backoff = min(backoff*2, backoffMax)
			continue
		case err != nil:
			return err
		}
		backoff = backoffMin
		if sent > 0 {
			if err := s.writeState(map[string]any{"token": tokenOK, "lastSentAt": iso(s.now())}); err != nil {
				return err
			}
			lastSent = s.now()
		}
		if once {
			return nil
		}
		if s.now().Sub(lastSent) >= idleExit {
			s.log("idle for %d s, exiting", int(idleExit.Seconds()))
			return nil
		}
		if err := sleep(ctx, sendInterval); err != nil {
			return err
		}
	}
}

func sleepContext(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

// sessionIDs are the queued sessions, in name order.
func (s *run) sessionIDs() ([]string, error) {
	entries, err := os.ReadDir(capture.SessionsDir(s.stateDir))
	if errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	var ids []string
	for _, entry := range entries {
		if entry.IsDir() {
			ids = append(ids, entry.Name())
		}
	}
	sort.Strings(ids)
	return ids, nil
}

// sendRound is one pass over every queued session; it returns the number of batches Kaya took.
func (s *run) sendRound(ctx context.Context) (int, error) {
	ids, err := s.sessionIDs()
	if err != nil {
		return 0, err
	}
	sent := 0
	for _, id := range ids {
		dir := capture.SessionDir(s.stateDir, id)
		for range batchesPerSessionRound {
			ok, err := s.sendSession(ctx, dir, id)
			if err != nil {
				return sent, err
			}
			if !ok {
				break
			}
			sent++
		}
		s.prune(dir, id)
	}
	return sent, nil
}

// sendSession sends one batch of a session; ok is true when Kaya took one.
func (s *run) sendSession(ctx context.Context, dir, sessionID string) (bool, error) {
	cur := s.readCursor(dir)
	if cur.Parked != nil {
		switch {
		case cur.Parked.TokenFingerprint != s.tokenFP:
			s.log("%s: the token changed, the parked session is sent again", sessionID)
		case s.retriesMachineNamePark(cur.Parked):
			s.log("%s: parked after %s by %s, oba %s sends it again", sessionID, codeMachineName, parkedBy(cur.Parked), s.version)
		default:
			return false, nil
		}
		cur.Parked = nil
	}
	b, what, reason := s.buildBatch(dir, sessionID, cur)
	if b == nil {
		if reason != "" {
			s.reportOnce(sessionID, reason, "not sent, "+reason)
		}
		return false, nil
	}
	body, err := encodeCompact(b)
	if err != nil {
		return false, fmt.Errorf("encode the batch of %s: %w", sessionID, err)
	}
	data, err := s.client.PostIngestBatch(ctx, body)
	if err == nil {
		return true, s.acknowledge(dir, sessionID, cur, b, what, data)
	}
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		if ctx.Err() != nil {
			return false, ctx.Err()
		}
		return false, &retryError{reason: fmt.Sprintf("Kaya unreachable: %v", err)}
	}
	answer := strings.TrimSpace(fmt.Sprintf("%d %s", apiErr.Status, apiErr.Code))
	switch {
	case apiErr.Status == 401:
		code := apiErr.Code
		if code == "" {
			code = strconv.Itoa(apiErr.Status)
		}
		return false, &stopError{reason: fmt.Sprintf("Kaya refused the token (%s)", code)}
	case parkStatuses[apiErr.Status]:
		s.log("%s: parked, Kaya answered %s", sessionID, answer)
		var code *string
		if apiErr.Code != "" {
			code = &apiErr.Code
		}
		cur.Parked = &parked{Status: apiErr.Status, Code: code, At: iso(s.now()), TokenFingerprint: s.tokenFP, ObaVersion: s.version}
		return false, writeCursor(dir, cur)
	case apiErr.Status == 403 && apiErr.Code == codeUnknownGit:
		// No user of the organization holds the session's git email yet. The batch stays queued
		// and goes out once someone registers the email; parking would need a new token instead.
		email, _ := apiErr.ErrorValues["gitAuthorEmail"].(string)
		return false, &retryError{reason: fmt.Sprintf("Kaya answered %s: no Kaya user holds the git author email %q", answer, email)}
	}
	return false, &retryError{reason: "Kaya answered " + answer}
}

// retriesMachineNamePark reports whether this oba sends a park after a machine name refusal once
// more: another version parked it, or one that did not record its version. The park this oba
// writes on a second refusal holds its own version, which ends the retries until the next one.
func (s *run) retriesMachineNamePark(p *parked) bool {
	return s.version != "" && p.Code != nil && *p.Code == codeMachineName && p.ObaVersion != s.version
}

// parkedBy names the oba that parked a session in the log.
func parkedBy(p *parked) string {
	if p.ObaVersion == "" {
		return "an oba that did not record its version"
	}
	return "oba " + p.ObaVersion
}

// acknowledge stores what Kaya took: the seq it holds, the handoffs and plans sent, and removes
// the usage file unless the status line wrote a newer one meanwhile.
func (s *run) acknowledge(dir, sessionID string, cur *cursor, b *batch, what *carried, data json.RawMessage) error {
	acked, ok := acknowledgedSeq(data)
	if !ok {
		acked = cur.AcknowledgedSeq
		for _, seq := range b.seqs {
			acked = max(acked, seq)
		}
	}
	cur.AcknowledgedSeq = max(cur.AcknowledgedSeq, acked)
	for _, item := range what.handoffs {
		cur.Handoffs[item.rel] = item.mtimeNs
	}
	for _, item := range what.plans {
		cur.Plans[item.rel] = item.mtimeNs
	}
	cur.SentAt = iso(s.now())
	if err := writeCursor(dir, cur); err != nil {
		return err
	}
	if what.usageRaw == nil {
		return nil
	}
	path := capture.UsagePath(s.stateDir, sessionID)
	current, err := os.ReadFile(path) // #nosec G304 -- the status line's usage file in the state directory
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	if err != nil {
		s.log("%s unreadable, kept: %v", path, err)
		return nil
	}
	if string(current) == string(what.usageRaw) {
		if err := removeFile(path); err != nil {
			s.log("%s not removed, it is sent again: %v", path, err)
		}
	}
	return nil
}

// acknowledgedSeq is data.acknowledgedSeq when it is an integer.
func acknowledgedSeq(data json.RawMessage) (int64, bool) {
	if len(data) == 0 {
		return 0, false
	}
	var answer map[string]any
	if err := decodeNumbers(data, &answer); err != nil {
		return 0, false
	}
	number, ok := answer["acknowledgedSeq"].(json.Number)
	if !ok {
		return 0, false
	}
	seq, err := strconv.ParseInt(number.String(), 10, 64)
	return seq, err == nil
}

// prune removes a session that ended more than pruneAfter ago and that Kaya holds completely.
func (s *run) prune(dir, sessionID string) {
	var summary map[string]any
	if _, err := readJSON(filepath.Join(dir, capture.SummaryFile), &summary); err != nil {
		// rule-10-no-op -- reason: the round that tried to send this session already logged the damaged summary; the session is kept.
		return
	}
	if !truthy(summary["ended"]) {
		return
	}
	if _, err := os.Stat(capture.UsagePath(s.stateDir, sessionID)); err == nil {
		return
	}
	info, err := os.Stat(filepath.Join(dir, capture.EventsFile))
	if err != nil || s.now().Sub(info.ModTime()) < pruneAfter {
		return
	}
	cur := s.readCursor(dir)
	events, err := s.pendingEvents(dir, cur.AcknowledgedSeq, 1, false)
	if err != nil || len(events) > 0 {
		return
	}
	if handoffs, err := changedHandoffs(dir, cur.Handoffs, 1); err != nil || len(handoffs) > 0 {
		return
	}
	if plans, err := changedPlans(dir, cur.Plans, 1); err != nil || len(plans) > 0 {
		return
	}
	if err := os.RemoveAll(dir); err != nil {
		s.log("%s: not removed from the queue: %v", sessionID, err)
		return
	}
	s.log("%s: removed from the queue, acknowledged and ended", sessionID)
}

// startCommand builds the detached sender process; tests replace it.
var startCommand = func(self string) *exec.Cmd {
	return exec.Command(self, "kaya", "sender") // #nosec G204 -- this binary with fixed arguments
}

// StartDetached starts `oba kaya sender` in its own session when none runs, so a hook never waits
// for Kaya and Claude Code never waits for the sender. started is false when KAYA_SENDER=off,
// when a sender holds sender.lock, or while the start back-off of sender.started runs.
func StartDetached() (started bool, err error) {
	if os.Getenv(senderEnv) == "off" {
		return false, nil
	}
	stateDir, err := capture.EnsureStateDir()
	if err != nil {
		return false, err
	}
	free, err := lockFree(filepath.Join(stateDir, lockFile))
	if err != nil || !free {
		return false, err
	}
	startedPath := filepath.Join(stateDir, capture.SenderStartedFile)
	attempts := 0
	data, err := os.ReadFile(startedPath) // #nosec G304 -- fixed name in the state directory
	switch {
	case errors.Is(err, fs.ErrNotExist):
		// rule-10-no-op -- reason: no sender.started file means no earlier start, so there is no back-off to wait out.
	case err != nil:
		return false, fmt.Errorf("read %s: %w", startedPath, err)
	default:
		if n, convErr := strconv.Atoi(strings.TrimSpace(string(data))); convErr == nil {
			attempts = max(n, 0)
			wait := min(restartWait<<min(attempts, 10), restartWaitMax)
			info, err := os.Stat(startedPath)
			if err != nil {
				return false, fmt.Errorf("inspect %s: %w", startedPath, err)
			}
			if time.Since(info.ModTime()) < wait {
				return false, nil
			}
		}
	}
	if err := writeAtomic(startedPath, []byte(strconv.Itoa(attempts+1)+"\n")); err != nil {
		return false, err
	}
	self, err := os.Executable()
	if err != nil {
		return false, fmt.Errorf("find the oba binary: %w", err)
	}
	logPath := filepath.Join(stateDir, capture.LogFile)
	logFile, err := os.OpenFile(logPath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600) // #nosec G304 -- fixed name in the state directory
	if err != nil {
		return false, fmt.Errorf("open %s: %w", logPath, err)
	}
	defer func() { _ = logFile.Close() }()
	cmd := startCommand(self)
	cmd.Dir = stateDir
	cmd.Stdout, cmd.Stderr = logFile, logFile
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	if err := cmd.Start(); err != nil {
		return false, fmt.Errorf("start oba kaya sender: %w", err)
	}
	return true, cmd.Process.Release()
}

// lockFree reports whether no process holds the lock file at path.
func lockFree(path string) (bool, error) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed name in the state directory
	if err != nil {
		return false, fmt.Errorf("open %s: %w", path, err)
	}
	defer func() { _ = file.Close() }()
	if err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil { // #nosec G115 -- a file descriptor fits an int
		if errors.Is(err, syscall.EWOULDBLOCK) {
			return false, nil
		}
		return false, fmt.Errorf("lock %s: %w", path, err)
	}
	return true, syscall.Flock(int(file.Fd()), syscall.LOCK_UN) // #nosec G115 -- a file descriptor fits an int
}
