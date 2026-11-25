// Package sender sends the session queue the capture writes (see the capture package) to Kaya,
// POST /kaya/ingest/batches, as the developer's machine token.
//
// Every two seconds it sends, per session, the events after the session's acknowledged seq (at
// most 500), the handoff and plan snapshots changed since Kaya last acknowledged them and the
// session's usage file, and keeps what Kaya acknowledged in the session's cursor.json, so it
// replays in order after an outage; Kaya stores each seq once. A network error, a 429 and every
// 5xx but 500, such as the 502, 503 or 504 of a gateway while Kaya is down, back the whole sender
// off from 2 s to 5 min. A 500 and a 403 KAY_SEC_007 (the refusal of older Kaya releases for a git
// email that no user of the organization held) answer one session's batch, so that session alone
// backs off the same way while the others are sent. A 500 for a second session while Kaya takes
// no batch is Kaya itself failing, so the whole sender backs off as for a gateway, and the next
// round starts after that session. A round tries the sessions without such a 500 first, and a 500
// of a session that had none yet ends the round with a wait of 2 s that leaves the back-off as it
// is: sessions that fail on their own content look like Kaya failing until a healthy session is
// tried, and that session must not wait one back-off step per failing session sorted before it.
// Either way the session stays queued. A 401
// stops the sender until the token changes. A session whose batch Kaya refuses for its content
// (400, 409, 413, 422) is parked until the token changes; one parked after
// the machine name refusal of an older Kaya release (KAY_VAL_005) is also sent once more by
// another oba version. One sender runs per machine: it holds sender.lock. It exits after ten
// minutes without a sent batch while no session waits for a retry.
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
	"slices"
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

	tokenEnv  = "KAYA_RECORDER_TOKEN"
	senderEnv = "KAYA_SENDER"
	// codeUnknownGit is the refusal of older Kaya releases for a batch whose git email no user of
	// the organization held. Later Kaya releases accept such a batch for the token's user, since
	// the token identifies the person, so a session refused with it stays queued for an updated
	// Kaya instead of being parked until a new token.
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

// retryError ends the round: Kaya cannot take any batch now and the sender backs off. newFailure
// marks a round ended by the first 500 of a session since Kaya last took a batch. That session may
// fail on its own content while a session not tried yet would be taken, so the sender tries again
// after 2 s and leaves its back-off as it is. The failing sessions are only forgotten when Kaya
// takes a batch, so there is at most one such short wait per queued session in between.
type retryError struct {
	reason     string
	newFailure bool
}

func (e *retryError) Error() string { return e.reason }

// sessionRetryError ends a session's turn in the round: Kaya could not take this session's batch,
// which says nothing about the batches of the others. The session backs off on its own and the
// round goes on with the next one. failed marks a 500, Kaya failing on the batch, as opposed to a
// refusal that names the session.
type sessionRetryError struct {
	reason string
	failed bool
}

func (e *sessionRetryError) Error() string { return e.reason }

// sessionRetry is when a session whose batch Kaya could not take is sent again, and the wait that
// led there.
type sessionRetry struct {
	at   time.Time
	wait time.Duration
}

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
	// retries are the sessions that wait before their batch is sent again.
	retries map[string]sessionRetry
	// failing are the sessions whose batch Kaya answered with a 500 since it last took a batch.
	failing map[string]bool
	// resumeAfter is the session whose 500 ended the last round as a fault of Kaya itself; the
	// next round starts after it.
	resumeAfter string
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

	s := &run{stateDir: stateDir, version: cfg.Version, now: cfg.Now, hostname: cfg.Hostname, resolver: cfg.Resolver, reported: map[string]bool{}, retries: map[string]sessionRetry{}, failing: map[string]bool{}, unresolvedHosts: map[string]bool{}}
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
			wait := backoff
			if retry.newFailure {
				wait = backoffMin
			}
			s.log("%s; retrying in %d s", retry.reason, int(wait.Seconds()))
			if once {
				return nil
			}
			if err := sleep(ctx, wait); err != nil {
				return err
			}
			if !retry.newFailure {
				backoff = min(backoff*2, backoffMax)
			}
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
		// A session that waits for its retry keeps the sender running, as an unreachable Kaya does.
		if len(s.retries) == 0 && s.now().Sub(lastSent) >= idleExit {
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

// sendRound is one pass over every queued session; it returns the number of batches Kaya took. A
// session whose batch Kaya could not take is skipped until its retry is due, so it never holds
// back the sessions after it; only an error every session would meet alike ends the round.
//
// A 500 for a second session while Kaya has taken no batch, in this round or since the rounds
// before, is such an error: Kaya itself fails, as after a deploy whose bug every request meets.
// Sending each queued session into it would multiply the requests by the length of the queue, so
// the round ends and the whole sender backs off as for a gateway: once every queued session with
// work has shown its 500, one request per back-off step. Until then each round ends on the first
// 500 of another session and waits only 2 s, because sessions that fail on their own content look
// the same until a session Kaya takes is tried. One failing session among sessions Kaya takes
// still backs off alone.
func (s *run) sendRound(ctx context.Context) (int, error) {
	ids, err := s.sessionIDs()
	if err != nil {
		return 0, err
	}
	// A session that left the queue waits for nothing and no longer counts as failing.
	for id := range s.retries {
		if !slices.Contains(ids, id) {
			delete(s.retries, id)
		}
	}
	for id := range s.failing {
		if !slices.Contains(ids, id) {
			delete(s.failing, id)
		}
	}
	sent := 0
	for _, id := range s.roundOrder(ids) {
		if retry, waiting := s.retries[id]; waiting && s.now().Before(retry.at) {
			continue
		}
		dir := capture.SessionDir(s.stateDir, id)
		for range batchesPerSessionRound {
			ok, err := s.sendSession(ctx, dir, id)
			var later *sessionRetryError
			if errors.As(err, &later) {
				if later.failed {
					known := s.failing[id]
					s.failing[id] = true
					if sent == 0 && len(s.failing) >= 2 {
						// The session's own retry stays as it was, as for a gateway answer: the
						// whole sender's back-off is the wait.
						s.resumeAfter = id
						return sent, &retryError{reason: fmt.Sprintf("%s for %d sessions and took no batch", later.reason, len(s.failing)), newFailure: !known}
					}
				}
				s.retryLater(id, later.reason)
				break
			}
			if err != nil {
				return sent, err
			}
			delete(s.retries, id)
			if !ok {
				break
			}
			sent++
			clear(s.failing)
		}
		s.prune(dir, id)
	}
	s.resumeAfter = ""
	return sent, nil
}

// roundOrder is the order in which a round tries the queued sessions: those without a 500 since
// Kaya last took a batch before those with one. Each group is in name order, turned after a round
// that ended on a fault of Kaya itself so that it starts after the session whose 500 ended it. A
// fault is judged on the first sessions a round tries, so sessions whose own batches fail must not
// come first: they would end every round before a session Kaya takes is tried. The turn spreads
// the requests of the back-off over the failing sessions, so one that Kaya takes again is found.
func (s *run) roundOrder(ids []string) []string {
	order := ids
	if s.resumeAfter != "" {
		next, found := slices.BinarySearch(ids, s.resumeAfter)
		if found {
			next++
		}
		order = slices.Concat(ids[next:], ids[:next])
	}
	var fresh, failing []string
	for _, id := range order {
		if s.failing[id] {
			failing = append(failing, id)
		} else {
			fresh = append(fresh, id)
		}
	}
	return slices.Concat(fresh, failing)
}

// retryLater makes a session whose batch Kaya could not take wait before it is sent again: 2 s
// after the first failure, twice as long after each next one, up to 5 min, as the sender waits for
// an unreachable Kaya.
func (s *run) retryLater(sessionID, reason string) {
	wait := backoffMin
	if previous, ok := s.retries[sessionID]; ok {
		wait = min(previous.wait*2, backoffMax)
	}
	s.retries[sessionID] = sessionRetry{at: s.now().Add(wait), wait: wait}
	s.log("%s: %s; retrying in %d s", sessionID, reason, int(wait.Seconds()))
}

// sendSession sends one batch of a session; ok is true when Kaya took one.
func (s *run) sendSession(ctx context.Context, dir, sessionID string) (bool, error) {
	cur := s.readCursor(dir, sessionID)
	if cur.Parked != nil {
		var release string
		switch {
		case cur.Parked.TokenFingerprint != s.tokenFP:
			release = "the token changed, the parked session is sent again"
		case s.retriesMachineNamePark(cur.Parked):
			release = fmt.Sprintf("parked after %s by %s, oba %s sends it again", codeMachineName, parkedBy(cur.Parked), s.version)
		default:
			return false, nil
		}
		// The park stays on disk until Kaya answers a batch, so a session whose batch cannot be
		// built yet (its remote left the repository list, say) is released again every round. The
		// release is logged once per run, like the reason the session is not sent.
		s.reportOnce(sessionID, "release: "+release, release)
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
		// An older Kaya release refused the session's git email. The batch stays queued and goes
		// out once Kaya is updated; parking would need a new token instead. Other sessions may
		// carry an email Kaya knows, so only this one waits.
		email, _ := apiErr.ErrorValues["gitAuthorEmail"].(string)
		return false, &sessionRetryError{reason: fmt.Sprintf("Kaya answered %s: no Kaya user holds the git author email %q", answer, email)}
	case apiErr.Status == 500:
		// Kaya failed on this batch, as it does on content it cannot store; the batches of other
		// sessions may still go through.
		return false, &sessionRetryError{reason: "Kaya answered " + answer, failed: true}
	}
	// Any other answer says nothing about this batch. A 502, 503 or 504 comes from the gateway
	// while Kaya is down or overloaded, and a 429 from the limit of the machine token: every
	// session would meet it alike, so the whole sender backs off once instead of sending one batch
	// per queued session into the outage.
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
	cur := s.readCursor(dir, sessionID)
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
