package sender

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
)

// These tests drive the sender loop through its injected clock and sleep, with a queue built by
// hand, so the timings of the spec (back-off, idle exit, retention) are checked without waiting.

const flowSession = "11111111-aaaa-4bbb-8ccc-0000000000f1"

var errStopLoop = errors.New("test stops the loop")

// flowRemote is the repository of a queued session, recorded as the hook records it, and
// flowListed is the same repository as the cached repository list holds it.
const (
	flowRemote = "https://github.com/example/app.git"
	flowListed = "github.com/example/app"
)

// flowEnv isolates the state and config directories, caches a repository list that holds
// flowListed, and returns the state directory.
func flowEnv(t *testing.T) string {
	t.Helper()
	base, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	for key, value := range map[string]string{
		"HOME": filepath.Join(base, "home"), "XDG_STATE_HOME": filepath.Join(base, "xdg"), "XDG_CONFIG_HOME": filepath.Join(base, "config"),
		"GIT_CONFIG_GLOBAL": os.DevNull, "GIT_CONFIG_NOSYSTEM": "1", "KAYA_SENDER": "off", "KAYA_RECORDER_TOKEN": "",
	} {
		t.Setenv(key, value)
	}
	listRepositories(t, flowListed)
	return filepath.Join(base, "xdg", "kaya")
}

// listRepositories makes the cached repository list hold exactly remotes.
func listRepositories(t *testing.T, remotes ...string) {
	t.Helper()
	if err := delivery.SaveRepositoryList(delivery.RepositoryList{OrganizationID: "org-example", Remotes: remotes}); err != nil {
		t.Fatal(err)
	}
}

// queueSession writes a session with the given number of events and returns its directory.
func queueSession(t *testing.T, state, id string, events int, ended bool) string {
	t.Helper()
	dir := capture.SessionDir(state, id)
	var lines strings.Builder
	for seq := 1; seq <= events; seq++ {
		fmt.Fprintf(&lines, `{"seq":%d,"at":"2026-10-01T09:00:%02dZ","event":"UserPromptSubmit","prompt":"p%d"}`+"\n", seq, seq%60, seq)
	}
	writeFile(t, filepath.Join(dir, capture.EventsFile), lines.String())
	summary := fmt.Sprintf(`{"session_id":%q,"title":"t","started_at":"2026-10-01T09:00:00Z","last_event_at":"2026-10-01T09:00:01Z",`+
		`"ended":%v,"cwd":"/tmp","branch":"main","features":[],"briefed_features":[],"event_count":%d}`, id, ended, events)
	writeFile(t, filepath.Join(dir, capture.SummaryFile), summary)
	writeFile(t, filepath.Join(dir, capture.SessionFile),
		`{"sessionId":"`+id+`","machineName":"flow-host","remoteUrl":"`+flowRemote+`","repositoryName":"app","gitAuthorEmail":"dev@example.com"}`)
	return dir
}

func flowResolver() *seed.RemoteResolver {
	return &seed.RemoteResolver{Lookup: func(host string) (string, error) { return host, nil }}
}

func logText(t *testing.T, state string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(state, capture.LogFile))
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		t.Fatal(err)
	}
	return string(data)
}

// scriptedPoster answers calls from a list; the last answer repeats.
type scriptedPoster struct {
	answers []error
	calls   int
	bodies  [][]byte
}

func (p *scriptedPoster) PostIngestBatch(_ context.Context, body []byte) (json.RawMessage, error) {
	p.bodies = append(p.bodies, body)
	i := min(p.calls, len(p.answers)-1)
	p.calls++
	return nil, p.answers[i]
}

// sessionPoster answers each batch by its session: a session in refuse gets its error, the batch
// of any other is taken. It records the session and the time of every call.
type sessionPoster struct {
	clock  *fakeClock
	refuse map[string]error
	calls  []sessionCall
}

type sessionCall struct {
	session string
	at      time.Time
}

func (p *sessionPoster) PostIngestBatch(_ context.Context, body []byte) (json.RawMessage, error) {
	var b struct {
		SessionID string `json:"sessionId"`
	}
	if err := json.Unmarshal(body, &b); err != nil {
		return nil, err
	}
	p.calls = append(p.calls, sessionCall{session: b.SessionID, at: p.clock.now})
	return nil, p.refuse[b.SessionID]
}

func (c sessionCall) String() string {
	return c.session + " at " + c.at.UTC().Format(time.TimeOnly)
}

// sameCall reports whether two calls went to the same session at the same time.
func sameCall(a, b sessionCall) bool {
	return a.session == b.session && a.at.Equal(b.at)
}

func (p *sessionPoster) sessions() []string {
	var ids []string
	for _, call := range p.calls {
		ids = append(ids, call.session)
	}
	return ids
}

// fakeClock is the sender's time: Sleep moves it, and stops the loop after limit sleeps.
type fakeClock struct {
	now    time.Time
	sleeps []time.Duration
	limit  int
}

func (c *fakeClock) Now() time.Time { return c.now }

func (c *fakeClock) Sleep(_ context.Context, d time.Duration) error {
	c.sleeps = append(c.sleeps, d)
	c.now = c.now.Add(d)
	if c.limit > 0 && len(c.sleeps) >= c.limit {
		return errStopLoop
	}
	return nil
}

// flowVersion is the version of the oba that runs the flow's sender.
const flowVersion = "0.6.0 (abc1234, 2026-11-25T09:00:00Z)"

func flowConfig(clock *fakeClock, poster Poster) Config {
	return Config{
		APIURL:   "https://kaya.example.com",
		Token:    func(context.Context) (string, error) { return tokenA, nil },
		Version:  flowVersion,
		Client:   func(string, string) Poster { return poster },
		Resolver: flowResolver(),
		Sleep:    clock.Sleep,
		Now:      clock.Now,
		Hostname: func() (string, error) { return "flow-host", nil },
	}
}

// parkSession writes the cursor of a session parked under token, as the parking oba left it: code
// "" is a refusal without a code, and version "" a park of an oba that did not record its version.
func parkSession(t *testing.T, dir string, status int, code, token, version string) {
	t.Helper()
	codeJSON := "null"
	if code != "" {
		codeJSON = fmt.Sprintf("%q", code)
	}
	versionJSON := ""
	if version != "" {
		versionJSON = fmt.Sprintf(`,"obaVersion":%q`, version)
	}
	writeFile(t, filepath.Join(dir, cursorFile), fmt.Sprintf(
		`{"acknowledgedSeq":0,"handoffs":{},"plans":{},"parked":{"status":%d,"code":%s,"at":"2026-11-20T09:00:00Z","tokenFingerprint":%q%s}}`,
		status, codeJSON, fingerprint(token), versionJSON))
}

func seconds(values ...int) []time.Duration {
	out := make([]time.Duration, len(values))
	for i, v := range values {
		out[i] = time.Duration(v) * time.Second
	}
	return out
}

func equalDurations(a, b []time.Duration) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func TestBackoffDoublesFromTwoSecondsToFiveMinutes(t *testing.T) {
	state := flowEnv(t)
	queueSession(t, state, flowSession, 1, false)
	poster := &scriptedPoster{answers: []error{errors.New("connection refused")}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 10}
	err := Run(context.Background(), flowConfig(clock, poster))
	if !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v, want the loop stopped by the test", err)
	}
	want := seconds(2, 4, 8, 16, 32, 64, 128, 256, 300, 300)
	if !equalDurations(clock.sleeps, want) {
		t.Errorf("back-off sleeps = %v, want %v", clock.sleeps, want)
	}
	if !strings.Contains(logText(t, state), "retrying in 300 s") {
		t.Errorf("log does not name the 300 s wait:\n%s", logText(t, state))
	}
}

func TestBackoffForRateLimit(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 429, Code: "KAY_SEC_004"}}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 3}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if !equalDurations(clock.sleeps, seconds(2, 4, 8)) {
		t.Errorf("sleeps = %v, want 2 s, 4 s, 8 s", clock.sleeps)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.AcknowledgedSeq != 0 || cur.Parked != nil {
		t.Errorf("cursor after 429 = %+v, want queued and not parked", cur)
	}
}

// A 500 answers one session's batch: the session backs off from 2 s on its own, while the sender
// keeps its 2 s round.
func TestAServerErrorBacksOffTheSessionAlone(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	start := time.Unix(1_800_000_000, 0)
	clock := &fakeClock{now: start, limit: 8}
	poster := &sessionPoster{clock: clock, refuse: map[string]error{flowSession: &client.APIError{Status: 500}}}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if !equalDurations(clock.sleeps, seconds(2, 2, 2, 2, 2, 2, 2, 2)) {
		t.Errorf("sleeps = %v, want the 2 s round pause only", clock.sleeps)
	}
	var at []time.Duration
	for _, call := range poster.calls {
		at = append(at, call.at.Sub(start))
	}
	if !equalDurations(at, seconds(0, 2, 6, 14)) {
		t.Errorf("sent at %v, want 0 s, 2 s, 6 s and 14 s", at)
	}
	log := logText(t, state)
	for _, wait := range []int{2, 4, 8, 16} {
		line := fmt.Sprintf("%s: Kaya answered 500; retrying in %d s", flowSession, wait)
		if n := strings.Count(log, line); n != 1 {
			t.Errorf("%q logged %d times, want once, log:\n%s", line, n, log)
		}
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.AcknowledgedSeq != 0 || cur.Parked != nil {
		t.Errorf("cursor after 500 = %+v, want queued and not parked", cur)
	}
}

// A 502, 503 or 504 comes from the gateway while Kaya is down or overloaded, and every queued
// session would get it alike: the round ends after the first batch and the whole sender backs
// off, so the outage costs one request per back-off step, not one per queued session.
func TestAGatewayOutageBacksOffTheWholeQueue(t *testing.T) {
	ids := fiveSessions
	for _, status := range []int{502, 503, 504} {
		t.Run(fmt.Sprint(status), func(t *testing.T) {
			state := flowEnv(t)
			start := time.Unix(1_800_000_000, 0)
			clock := &fakeClock{now: start, limit: 4}
			poster := &sessionPoster{clock: clock, refuse: map[string]error{}}
			for _, id := range ids {
				queueSession(t, state, id, 1, false)
				poster.refuse[id] = &client.APIError{Status: status}
			}
			if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
				t.Fatalf("Run = %v", err)
			}
			if !equalDurations(clock.sleeps, seconds(2, 4, 8, 16)) {
				t.Errorf("sleeps = %v, want the back-off of the whole sender: 2 s, 4 s, 8 s, 16 s", clock.sleeps)
			}
			var at []time.Duration
			for _, call := range poster.calls {
				if call.session != ids[0] {
					t.Errorf("sent %s after the gateway answered %d, want the round ended", call.session, status)
				}
				at = append(at, call.at.Sub(start))
			}
			if !equalDurations(at, seconds(0, 2, 6, 14)) {
				t.Errorf("sent at %v, want one batch per round, at 0 s, 2 s, 6 s and 14 s", at)
			}
			log := logText(t, state)
			for _, wait := range []int{2, 4, 8, 16} {
				line := fmt.Sprintf("Kaya answered %d; retrying in %d s", status, wait)
				if n := strings.Count(log, line); n != 1 {
					t.Errorf("%q logged %d times, want once, log:\n%s", line, n, log)
				}
			}
			for _, id := range ids {
				if strings.Contains(log, id+": Kaya answered") {
					t.Errorf("%s backed off on its own, want the whole sender, log:\n%s", id, log)
				}
				if cur := (&run{}).readCursor(capture.SessionDir(state, id), id); cur.AcknowledgedSeq != 0 || cur.Parked != nil {
					t.Errorf("cursor of %s after %d = %+v, want queued and not parked", id, status, cur)
				}
			}
		})
	}
}

// fiveSessions are the ids of a queue of five sessions, in name order.
var fiveSessions = []string{
	flowSession,
	"22222222-aaaa-4bbb-8ccc-0000000000f2",
	"33333333-aaaa-4bbb-8ccc-0000000000f3",
	"44444444-aaaa-4bbb-8ccc-0000000000f4",
	"55555555-aaaa-4bbb-8ccc-0000000000f5",
}

// outageRun queues sessions of one event each, answers every batch with answer for steps sleeps
// of the sender, and returns the state directory, the poster and the clock.
func outageRun(t *testing.T, ids []string, answer error, steps int) (string, *sessionPoster, *fakeClock) {
	t.Helper()
	state := flowEnv(t)
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: steps}
	poster := &sessionPoster{clock: clock, refuse: map[string]error{}}
	for _, id := range ids {
		queueSession(t, state, id, 1, false)
		poster.refuse[id] = answer
	}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	return state, poster, clock
}

// A 500 for every queued session, as after a deploy whose bug every request meets, is Kaya itself
// failing: the whole sender backs off as for a gateway outage instead of each session on its own,
// which sent one request per queued session on every back-off step. Over the first hour (19
// back-off steps) five sessions cost the requests of a gateway outage plus one, the second 500
// that tells Kaya's fault from one failing batch, and the sender sleeps the same back-off.
func TestAServerErrorForEverySessionBacksOffTheWholeQueue(t *testing.T) {
	start := time.Unix(1_800_000_000, 0)
	_, gateway, gatewayClock := outageRun(t, fiveSessions, &client.APIError{Status: 503}, 19)
	state, poster, clock := outageRun(t, fiveSessions, &client.APIError{Status: 500}, 19)
	if len(gateway.calls) != 19 {
		t.Fatalf("a gateway outage cost %d requests in the first hour, want 19", len(gateway.calls))
	}
	if !equalDurations(clock.sleeps, gatewayClock.sleeps) {
		t.Errorf("sleeps = %v, want the back-off of a gateway outage %v", clock.sleeps, gatewayClock.sleeps)
	}
	if len(poster.calls) != len(gateway.calls)+1 {
		t.Errorf("a 500 for every session cost %d requests in the first hour, want %d, those of a gateway outage plus one",
			len(poster.calls), len(gateway.calls)+1)
	}
	// The first round stops at the second 500; each next round tries one session, the one after
	// the session whose 500 ended the round before.
	want := []sessionCall{
		{fiveSessions[0], start}, {fiveSessions[1], start}, {fiveSessions[2], start.Add(2 * time.Second)},
		{fiveSessions[3], start.Add(6 * time.Second)}, {fiveSessions[4], start.Add(14 * time.Second)},
		{fiveSessions[0], start.Add(30 * time.Second)}, {fiveSessions[1], start.Add(62 * time.Second)},
	}
	for i, call := range want {
		if i >= len(poster.calls) || !sameCall(poster.calls[i], call) {
			t.Fatalf("calls = %v, want them to start with %v", poster.calls, want)
		}
	}
	log := logText(t, state)
	for i, wait := range []int{2, 4, 8, 16} {
		line := fmt.Sprintf("Kaya answered 500 for %d sessions and took no batch; retrying in %d s", i+2, wait)
		if n := strings.Count(log, line); n != 1 {
			t.Errorf("%q logged %d times, want once, log:\n%s", line, n, log)
		}
	}
	// Only the first 500 came before the fault showed, so only that session backed off on its own.
	if line := fiveSessions[0] + ": Kaya answered 500; retrying in 2 s"; strings.Count(log, line) != 1 {
		t.Errorf("log lacks %q once:\n%s", line, log)
	}
	for _, id := range fiveSessions[1:] {
		if strings.Contains(log, id+": Kaya answered") {
			t.Errorf("%s backed off on its own, want the whole sender, log:\n%s", id, log)
		}
	}
	for _, id := range fiveSessions {
		if cur := (&run{}).readCursor(capture.SessionDir(state, id), id); cur.AcknowledgedSeq != 0 || cur.Parked != nil {
			t.Errorf("cursor of %s = %+v, want queued and not parked", id, cur)
		}
	}
}

// One session whose batch Kaya keeps failing among sessions it takes backs off alone, also when it
// sorts first: the others are sent in the first round and the sender keeps its 2 s round.
func TestOneFailingSessionAmongOthersBacksOffAlone(t *testing.T) {
	state := flowEnv(t)
	start := time.Unix(1_800_000_000, 0)
	clock := &fakeClock{now: start, limit: 4}
	poster := &sessionPoster{clock: clock, refuse: map[string]error{fiveSessions[0]: &client.APIError{Status: 500}}}
	for _, id := range fiveSessions {
		queueSession(t, state, id, 1, false)
	}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if !equalDurations(clock.sleeps, seconds(2, 2, 2, 2)) {
		t.Errorf("sleeps = %v, want the 2 s round pause only", clock.sleeps)
	}
	for _, id := range fiveSessions[1:] {
		if cur := (&run{}).readCursor(capture.SessionDir(state, id), id); cur.AcknowledgedSeq != 1 {
			t.Errorf("cursor of %s = %+v, want seq 1 acknowledged", id, cur)
		}
	}
	var failing []time.Duration
	for _, call := range poster.calls {
		if call.session == fiveSessions[0] {
			failing = append(failing, call.at.Sub(start))
		}
	}
	if !equalDurations(failing, seconds(0, 2, 6)) {
		t.Errorf("the failing session was sent at %v, want its own back-off: 0 s, 2 s, 6 s", failing)
	}
	if log := logText(t, state); strings.Contains(log, "took no batch") {
		t.Errorf("one failing session backed off the whole sender, log:\n%s", log)
	}
}

// Two sessions whose own batches Kaya fails, sorted first, look like a fault of Kaya in the first
// round, but they must not hold back the session after them: the next round starts with it.
func TestSessionsWhoseBatchesFailFirstDoNotHoldBackTheQueue(t *testing.T) {
	state := flowEnv(t)
	ids := fiveSessions[:3]
	start := time.Unix(1_800_000_000, 0)
	clock := &fakeClock{now: start, limit: 2}
	poster := &sessionPoster{clock: clock, refuse: map[string]error{
		ids[0]: &client.APIError{Status: 500}, ids[1]: &client.APIError{Status: 500},
	}}
	for _, id := range ids[:2] {
		queueSession(t, state, id, 1, false)
	}
	good := queueSession(t, state, ids[2], 2, false)
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if cur := (&run{}).readCursor(good, ids[2]); cur.AcknowledgedSeq != 2 {
		t.Errorf("cursor of the session after the failing ones = %+v, want seq 2 acknowledged", cur)
	}
	want := []sessionCall{
		{ids[0], start}, {ids[1], start},
		{ids[2], start.Add(2 * time.Second)}, {ids[0], start.Add(2 * time.Second)}, {ids[1], start.Add(2 * time.Second)},
	}
	if !slices.EqualFunc(poster.calls, want, sameCall) {
		t.Errorf("calls = %v, want %v", poster.calls, want)
	}
	// Once Kaya took a batch, the two back off on their own.
	if !equalDurations(clock.sleeps, seconds(2, 2)) {
		t.Errorf("sleeps = %v, want the whole sender's 2 s back-off and then the 2 s round", clock.sleeps)
	}
	log := logText(t, state)
	for _, line := range []string{ids[0] + ": Kaya answered 500; retrying in 4 s", ids[1] + ": Kaya answered 500; retrying in 2 s"} {
		if !strings.Contains(log, line) {
			t.Errorf("log lacks %q:\n%s", line, log)
		}
	}
}

// A session that waits for its retry is work left: the sender does not exit as idle, as it does
// not while Kaya cannot be reached.
func TestASessionThatWaitsForItsRetryKeepsTheSenderRunning(t *testing.T) {
	state := flowEnv(t)
	queueSession(t, state, flowSession, 1, false)
	// 400 rounds of 2 s are 800 s, past the 600 s idle time.
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 400}
	poster := &sessionPoster{clock: clock, refuse: map[string]error{flowSession: &client.APIError{Status: 500}}}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v, want the loop stopped by the test, not an idle exit", err)
	}
	if strings.Contains(logText(t, state), "idle for") {
		t.Errorf("the sender exited as idle while a session waited for its retry:\n%s", logText(t, state))
	}
	if !strings.Contains(logText(t, state), "retrying in 300 s") {
		t.Errorf("log does not name the 300 s wait:\n%s", logText(t, state))
	}
}

func TestForbiddenUnknownGitEmailBacksOffThenSendsAndKeepsTheSessionQueued(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 2, false)
	refusal := &client.APIError{Status: 403, Code: "KAY_SEC_007", ErrorValues: map[string]any{"gitAuthorEmail": "dev@example.com"}}
	poster := &scriptedPoster{answers: []error{refusal, refusal, nil}}
	// Refused at 0 s and 2 s, the session then waits 4 s, and its batch is taken at 6 s; the
	// sender keeps its 2 s round throughout.
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 4}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if !equalDurations(clock.sleeps, seconds(2, 2, 2, 2)) {
		t.Errorf("sleeps = %v, want the 2 s round pause only", clock.sleeps)
	}
	if poster.calls != 3 {
		t.Errorf("calls = %d, want two refusals and the accepted batch", poster.calls)
	}
	log := logText(t, state)
	if strings.Count(log, flowSession+`: Kaya answered 403 KAY_SEC_007: no Kaya user holds the git author email "dev@example.com"`) != 2 {
		t.Errorf("log does not name the session, the code and the email twice:\n%s", log)
	}
	if strings.Contains(log, "parked") {
		t.Errorf("a 403 KAY_SEC_007 must never park:\n%s", log)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.AcknowledgedSeq != 2 || cur.Parked != nil {
		t.Errorf("cursor = %+v, want seq 2 acknowledged after the accepted batch", cur)
	}
}

func TestBackoffResetsAfterAnAcceptedRound(t *testing.T) {
	state := flowEnv(t)
	queueSession(t, state, flowSession, 1, false)
	poster := &scriptedPoster{answers: []error{errors.New("down"), errors.New("down"), errors.New("down"), nil}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 4}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if !equalDurations(clock.sleeps, seconds(2, 4, 8, 2)) {
		t.Errorf("sleeps = %v, want the back-off 2 s, 4 s, 8 s and then the 2 s round", clock.sleeps)
	}
}

func TestParkingStatusesWaitForANewToken(t *testing.T) {
	for _, status := range []int{400, 409, 413, 422} {
		t.Run(fmt.Sprint(status), func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, 1, false)
			poster := &scriptedPoster{answers: []error{&client.APIError{Status: status}}}
			clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
			cfg := flowConfig(clock, poster)
			cfg.Once = true
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 1 {
				t.Fatalf("calls = %d, want 1", poster.calls)
			}
			if cur := (&run{}).readCursor(dir, flowSession); cur.Parked == nil || cur.Parked.Status != status || cur.Parked.ObaVersion != flowVersion {
				t.Fatalf("cursor = %+v, want parked with %d by oba %s", cur, status, flowVersion)
			}
			// The same token leaves the session parked, a new one sends it again.
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 1 {
				t.Errorf("a parked session was sent again with the same token (%d calls)", poster.calls)
			}
			cfg.Token = func(context.Context) (string, error) { return tokenB, nil }
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 2 {
				t.Errorf("calls = %d after the token changed, want 2", poster.calls)
			}
			if strings.Contains(logText(t, state), tokenA) || strings.Contains(logText(t, state), tokenB) {
				t.Error("recorder.log holds a token")
			}
		})
	}
}

func TestAMachineNameParkOfAnotherObaIsSentAgainWithTheSameToken(t *testing.T) {
	for name, parkedBy := range map[string]string{"no version recorded": "", "an older version": "0.5.0 (0123abc, 2026-11-01T09:00:00Z)"} {
		t.Run(name, func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, 2, false)
			parkSession(t, dir, 409, codeMachineName, tokenA, parkedBy)
			poster := &scriptedPoster{answers: []error{nil}}
			cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
			cfg.Once = true
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 1 {
				t.Fatalf("calls = %d, want the parked session sent once", poster.calls)
			}
			if cur := (&run{}).readCursor(dir, flowSession); cur.Parked != nil || cur.AcknowledgedSeq != 2 {
				t.Errorf("cursor = %+v, want seq 2 acknowledged and the park gone", cur)
			}
			// The name goes out as the hook recorded it: Kaya takes it as a label.
			if !strings.Contains(string(poster.bodies[0]), `"machineName":"flow-host"`) {
				t.Errorf("batch does not carry the recorded machine name:\n%s", poster.bodies[0])
			}
			if !strings.Contains(logText(t, state), "parked after KAY_VAL_005 by ") {
				t.Errorf("log does not say why the parked session is sent again:\n%s", logText(t, state))
			}
		})
	}
}

func TestAMachineNameParkStaysParkedUnderTheObaThatParkedIt(t *testing.T) {
	for name, versions := range map[string]struct{ parked, running string }{
		"same version":            {flowVersion, flowVersion},
		"running version unknown": {"", ""},
	} {
		t.Run(name, func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, 1, false)
			parkSession(t, dir, 409, codeMachineName, tokenA, versions.parked)
			poster := &scriptedPoster{answers: []error{nil}}
			cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
			cfg.Once = true
			cfg.Version = versions.running
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 0 {
				t.Errorf("calls = %d, want the session kept parked", poster.calls)
			}
			if cur := (&run{}).readCursor(dir, flowSession); cur.Parked == nil || cur.Parked.ObaVersion != versions.parked {
				t.Errorf("cursor = %+v, want the park unchanged", cur)
			}
		})
	}
}

func TestASecondMachineNameRefusalParksAgainWithoutALoop(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	parkSession(t, dir, 409, codeMachineName, tokenA, "")
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 409, Code: codeMachineName}}}
	// Three rounds of the loop: the first sends the session once more, the other two find it parked.
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 3}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if poster.calls != 1 {
		t.Fatalf("calls = %d over three rounds, want the one retry", poster.calls)
	}
	if !equalDurations(clock.sleeps, seconds(2, 2, 2)) {
		t.Errorf("sleeps = %v, want the 2 s round pause without a back-off", clock.sleeps)
	}
	cur := (&run{}).readCursor(dir, flowSession)
	if cur.Parked == nil || cur.Parked.Status != 409 || cur.Parked.Code == nil || *cur.Parked.Code != codeMachineName || cur.Parked.ObaVersion != flowVersion {
		t.Fatalf("cursor = %+v, want parked again on 409 %s by oba %s", cur, codeMachineName, flowVersion)
	}
	// The next oba version sends it once more, with the same token.
	cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
	cfg.Once = true
	cfg.Version = "0.6.1 (def5678, 2026-12-01T09:00:00Z)"
	for range 2 {
		if err := Run(context.Background(), cfg); err != nil {
			t.Fatal(err)
		}
	}
	if poster.calls != 2 {
		t.Errorf("calls = %d after two runs of the next version, want 2", poster.calls)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.Parked == nil || cur.Parked.ObaVersion != cfg.Version {
		t.Errorf("cursor = %+v, want parked by oba %s", cur, cfg.Version)
	}
}

// Every local build reports the version "dev", so local builds count as one version: the first
// sends a machine name park of a release once more, and after a second refusal they all wait. The
// park then holds "dev", so a release still sends it once more.
func TestALocalBuildSendsAMachineNameParkOnceAndThenWaits(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	parkSession(t, dir, 409, codeMachineName, tokenA, flowVersion)
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 409, Code: codeMachineName}, nil}}
	cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
	cfg.Once = true
	cfg.Version = "dev"
	for range 3 {
		if err := Run(context.Background(), cfg); err != nil {
			t.Fatal(err)
		}
	}
	if poster.calls != 1 {
		t.Fatalf("calls = %d over three runs of a local build, want the one retry", poster.calls)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.Parked == nil || cur.Parked.ObaVersion != "dev" {
		t.Fatalf("cursor = %+v, want parked by oba dev", cur)
	}
	cfg.Version = flowVersion
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if poster.calls != 2 {
		t.Errorf("calls = %d after the release ran, want 2", poster.calls)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.Parked != nil || cur.AcknowledgedSeq != 1 {
		t.Errorf("cursor = %+v, want seq 1 acknowledged and the park gone", cur)
	}
}

// A released park whose batch cannot be built stays parked on disk, so every round releases it
// again: the release and the reason the session is not sent are each logged once per run.
func TestAReleasedParkThatCannotBeSentIsLoggedOnce(t *testing.T) {
	for name, park := range map[string]struct{ code, token, version, release string }{
		"token changed":        {"", tokenB, flowVersion, "the token changed, the parked session is sent again"},
		"machine name refusal": {codeMachineName, tokenA, "", "parked after " + codeMachineName + " by an oba that did not record its version"},
	} {
		t.Run(name, func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, 1, false)
			parkSession(t, dir, 409, park.code, park.token, park.version)
			// The session's repository left the organization's repository list.
			listRepositories(t, "github.com/example/other")
			poster := &scriptedPoster{answers: []error{nil}}
			// Two rounds of the loop.
			clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 2}
			if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
				t.Fatalf("Run = %v", err)
			}
			if poster.calls != 0 {
				t.Fatalf("calls = %d, want nothing sent for an unlisted repository", poster.calls)
			}
			if cur := (&run{}).readCursor(dir, flowSession); cur.Parked == nil || cur.Parked.TokenFingerprint != fingerprint(park.token) {
				t.Errorf("cursor = %+v, want the park unchanged", cur)
			}
			log := logText(t, state)
			for _, line := range []string{park.release, "not sent, " + unlisted} {
				if n := strings.Count(log, flowSession+": "+line); n != 1 {
					t.Errorf("%q logged %d times over two rounds, want once, log:\n%s", line, n, log)
				}
			}
		})
	}
}

// A damaged cursor is read again every round while its session cannot be sent: each damage is
// logged once per run, and a different damage once more.
func TestADamagedCursorIsLoggedOncePerDamage(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	path := filepath.Join(dir, cursorFile)
	writeFile(t, path, "{")
	// The session's repository left the organization's repository list.
	listRepositories(t, "github.com/example/other")
	poster := &scriptedPoster{answers: []error{nil}}
	// Four rounds of the loop, the cursor damaged in another way after the second.
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 4}
	cfg := flowConfig(clock, poster)
	cfg.Sleep = func(ctx context.Context, d time.Duration) error {
		if len(clock.sleeps) == 1 {
			writeFile(t, path, "[]")
		}
		return clock.Sleep(ctx, d)
	}
	if err := Run(context.Background(), cfg); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if poster.calls != 0 {
		t.Fatalf("calls = %d, want nothing sent for an unlisted repository", poster.calls)
	}
	log := logText(t, state)
	if n := strings.Count(log, flowSession+": "+path+" unreadable, sending the session again: "); n != 2 {
		t.Errorf("the damaged cursor was logged %d times over four rounds and two damages, want twice, log:\n%s", n, log)
	}
	for _, damage := range []string{"unexpected EOF", "cannot unmarshal array"} {
		if n := strings.Count(log, damage); n != 1 {
			t.Errorf("%q logged %d times, want once, log:\n%s", damage, n, log)
		}
	}
}

func TestAParkOnAnotherRefusalWaitsForANewTokenUnderANewerOba(t *testing.T) {
	for _, refusal := range []struct {
		status int
		code   string
	}{{400, "KAY_VAL_002"}, {413, ""}, {422, "KAY_VAL_001"}} {
		t.Run(fmt.Sprint(refusal.status), func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, 1, false)
			parkSession(t, dir, refusal.status, refusal.code, tokenA, "")
			poster := &scriptedPoster{answers: []error{nil}}
			cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
			cfg.Once = true
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 0 {
				t.Fatalf("calls = %d, want the session kept parked under a newer oba", poster.calls)
			}
			cfg.Token = func(context.Context) (string, error) { return tokenB, nil }
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 1 {
				t.Errorf("calls = %d after the token changed, want 1", poster.calls)
			}
		})
	}
	// A refused token stops the sender before any park is looked at, a machine name park included.
	t.Run("refused token", func(t *testing.T) {
		state := flowEnv(t)
		dir := queueSession(t, state, flowSession, 1, false)
		parkSession(t, dir, 409, codeMachineName, tokenA, "")
		writeFile(t, filepath.Join(state, capture.StateFile), fmt.Sprintf(`{"token":%q,"refusedFingerprint":%q}`, tokenRefused, fingerprint(tokenA)))
		poster := &scriptedPoster{answers: []error{nil}}
		cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
		cfg.Once = true
		if err := Run(context.Background(), cfg); err != nil {
			t.Fatal(err)
		}
		if poster.calls != 0 {
			t.Fatalf("calls = %d, want nothing sent with the refused token", poster.calls)
		}
		cfg.Token = func(context.Context) (string, error) { return tokenB, nil }
		if err := Run(context.Background(), cfg); err != nil {
			t.Fatal(err)
		}
		if poster.calls != 1 {
			t.Errorf("calls = %d after the token changed, want 1", poster.calls)
		}
	})
}

func TestUnauthorizedStopsUntilTheTokenChanges(t *testing.T) {
	state := flowEnv(t)
	queueSession(t, state, flowSession, 1, false)
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 401, Code: "KAY_SEC_001"}, nil}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
	cfg := flowConfig(clock, poster)
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if err := Run(context.Background(), cfg); err != nil || poster.calls != 1 {
		t.Fatalf("second Run = %v with %d calls; want no call with the refused token", err, poster.calls)
	}
	cfg.Token = func(context.Context) (string, error) { return tokenB, nil }
	cfg.Once = true
	if err := Run(context.Background(), cfg); err != nil || poster.calls != 2 {
		t.Fatalf("Run with a new token = %v with %d calls; want a second call", err, poster.calls)
	}
}

func TestIdleExitAfterTenMinutesWithoutASend(t *testing.T) {
	state := flowEnv(t)
	if err := os.MkdirAll(capture.SessionsDir(state), 0o700); err != nil {
		t.Fatal(err)
	}
	poster := &scriptedPoster{answers: []error{nil}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
	if err := Run(context.Background(), flowConfig(clock, poster)); err != nil {
		t.Fatalf("Run = %v, want a clean idle exit", err)
	}
	var total time.Duration
	for _, d := range clock.sleeps {
		if d != sendInterval {
			t.Fatalf("sleep %v, want the %v round pause only", d, sendInterval)
		}
		total += d
	}
	if total != 10*time.Minute || len(clock.sleeps) != 300 {
		t.Errorf("slept %v in %d rounds, want 10 minutes in 300", total, len(clock.sleeps))
	}
	if !strings.Contains(logText(t, state), "idle for 600 s, exiting") {
		t.Errorf("log lacks the idle line:\n%s", logText(t, state))
	}
	if poster.calls != 0 {
		t.Errorf("calls = %d, want none", poster.calls)
	}
}

func TestIdleTimerRestartsWhenABatchIsSent(t *testing.T) {
	state := flowEnv(t)
	queueSession(t, state, flowSession, 1, false)
	poster := &scriptedPoster{answers: []error{nil}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
	cfg := flowConfig(clock, poster)
	// After five minutes a second event arrives, so the sender must stay ten minutes beyond it.
	cfg.Sleep = func(ctx context.Context, d time.Duration) error {
		if err := clock.Sleep(ctx, d); err != nil {
			return err
		}
		if clock.now.Sub(time.Unix(1_800_000_000, 0)) == 5*time.Minute {
			queueSession(t, state, flowSession, 2, false)
		}
		return nil
	}
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if poster.calls != 2 {
		t.Fatalf("calls = %d, want the first batch and the one after five minutes", poster.calls)
	}
	elapsed := clock.now.Sub(time.Unix(1_800_000_000, 0))
	if elapsed != 15*time.Minute {
		t.Errorf("exited after %v, want 15 minutes (5 until the second batch plus 10 idle)", elapsed)
	}
}

func TestPruneRemovesAnEndedAcknowledgedSessionAfterSevenDays(t *testing.T) {
	now := time.Unix(1_800_000_000, 0)
	cases := []struct {
		name   string
		age    time.Duration
		ended  bool
		events int
		usage  bool
		acked  int64
		remove bool
		// refuse makes Kaya answer 500 to the batch, so the unacknowledged events stay queued and
		// the round still reaches the prune of that session.
		refuse bool
	}{
		{"ended and old", 7*24*time.Hour + time.Hour, true, 3, false, 3, true, false},
		{"ended but newer than seven days", 6 * 24 * time.Hour, true, 3, false, 3, false, false},
		{"old but not ended", 8 * 24 * time.Hour, false, 3, false, 3, false, false},
		{"ended and old but events unacknowledged", 8 * 24 * time.Hour, true, 3, false, 2, false, true},
		{"ended and old but a usage file waits", 8 * 24 * time.Hour, true, 3, true, 3, false, true},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, tc.events, tc.ended)
			cur := &cursor{AcknowledgedSeq: tc.acked, Handoffs: map[string]int64{}, Plans: map[string]int64{}}
			if err := writeCursor(dir, cur); err != nil {
				t.Fatal(err)
			}
			if tc.usage {
				writeFile(t, capture.UsagePath(state, flowSession), `{"sessionId":"x"}`)
			}
			old := now.Add(-tc.age)
			if err := os.Chtimes(filepath.Join(dir, capture.EventsFile), old, old); err != nil {
				t.Fatal(err)
			}
			poster := &scriptedPoster{answers: []error{nil}}
			if tc.refuse {
				poster.answers = []error{&client.APIError{Status: 500}}
			}
			clock := &fakeClock{now: now}
			cfg := flowConfig(clock, poster)
			cfg.Once = true
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			_, statErr := os.Stat(dir)
			if removed := errors.Is(statErr, os.ErrNotExist); removed != tc.remove {
				t.Errorf("session removed = %v, want %v", removed, tc.remove)
			}
			if tc.remove && !strings.Contains(logText(t, state), flowSession+": removed from the queue") {
				t.Errorf("log lacks the removal line:\n%s", logText(t, state))
			}
		})
	}
}

func TestPruneKeepsASessionWithAnUnsentSnapshot(t *testing.T) {
	now := time.Unix(1_800_000_000, 0)
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, true)
	writeFile(t, filepath.Join(dir, "handoffs", "a", "b.md"), "# handoff\n")
	if err := writeCursor(dir, &cursor{AcknowledgedSeq: 1, Handoffs: map[string]int64{}, Plans: map[string]int64{}}); err != nil {
		t.Fatal(err)
	}
	old := now.Add(-8 * 24 * time.Hour)
	if err := os.Chtimes(filepath.Join(dir, capture.EventsFile), old, old); err != nil {
		t.Fatal(err)
	}
	// Kaya refuses the handoff batch for now, so the unsent snapshot must keep the session.
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 500}}}
	clock := &fakeClock{now: now}
	cfg := flowConfig(clock, poster)
	cfg.Once = true
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(dir); err != nil {
		t.Errorf("a session with an unsent handoff was removed: %v", err)
	}
}

// ingestStore is a fake Kaya that stores every seq once and can drop the answer of a batch.
type ingestStore struct {
	mu        sync.Mutex
	seen      map[int64]int
	requests  int
	dropFirst bool
}

func (s *ingestStore) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Events []struct {
			Seq int64 `json:"seq"`
		} `json:"events"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	s.mu.Lock()
	s.requests++
	var highest int64
	for _, e := range body.Events {
		s.seen[e.Seq]++
		highest = max(highest, e.Seq)
	}
	drop := s.dropFirst && s.requests == 1
	s.mu.Unlock()
	if drop {
		// The batch is stored, but the connection dies before Kaya answers.
		conn, _, err := w.(http.Hijacker).Hijack()
		if err == nil {
			_ = conn.Close()
		}
		return
	}
	_, _ = fmt.Fprintf(w, `{"success":true,"data":{"acknowledgedSeq":%d}}`, highest)
}

func TestRecordedRemoteIsSentAsRecordedWhenSSHConfigMapsItsHost(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	const recorded = "git@github.com:example/app.git"
	writeFile(t, filepath.Join(dir, capture.SessionFile),
		`{"sessionId":"`+flowSession+`","machineName":"flow-host","remoteUrl":"`+recorded+`","repositoryName":"app","gitAuthorEmail":"dev@example.com"}`)
	poster := &scriptedPoster{answers: []error{nil}}
	cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
	cfg.Once = true
	// Stands in for ssh config that sends github.com over port 443:
	// `Host github.com` with `Hostname ssh.github.com`.
	var asked []string
	cfg.Resolver = &seed.RemoteResolver{Lookup: func(host string) (string, error) {
		asked = append(asked, host)
		if host == "github.com" {
			return "ssh.github.com", nil
		}
		return host, nil
	}}
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if len(poster.bodies) != 1 {
		t.Fatalf("Kaya received %d batches, want 1", len(poster.bodies))
	}
	var sent struct {
		Repository struct {
			RemoteURL string `json:"remoteUrl"`
		} `json:"repository"`
	}
	if err := json.Unmarshal(poster.bodies[0], &sent); err != nil {
		t.Fatal(err)
	}
	if sent.Repository.RemoteURL != recorded {
		t.Errorf("sent remote = %q, want the recorded %q", sent.Repository.RemoteURL, recorded)
	}
	if len(asked) != 0 {
		t.Errorf("ssh config was consulted for %q; a recorded remote is sent without resolving it", asked)
	}
}

// aliasResolver stands in for ssh config with `Host work-gh` and `Hostname github.com`, so no
// test reads the user's ssh config.
func aliasResolver() *seed.RemoteResolver {
	return &seed.RemoteResolver{Lookup: func(host string) (string, error) {
		if host == "work-gh" {
			return "github.com", nil
		}
		return host, nil
	}}
}

// recordSession replaces the session file of the queued session; without remote it records none,
// as a session whose first event was queued before its session file was written.
func recordSession(t *testing.T, dir, remote string) {
	t.Helper()
	recorded := ""
	if remote != "" {
		recorded = `"remoteUrl":"` + remote + `",`
	}
	writeFile(t, filepath.Join(dir, capture.SessionFile),
		`{"sessionId":"`+flowSession+`","machineName":"flow-host",`+recorded+`"gitAuthorEmail":"dev@example.com"}`)
}

// runOnce runs one sender round and returns the bodies Kaya received.
func runOnce(t *testing.T, resolver *seed.RemoteResolver) [][]byte {
	t.Helper()
	poster := &scriptedPoster{answers: []error{nil}}
	cfg := flowConfig(&fakeClock{now: time.Unix(1_800_000_000, 0)}, poster)
	cfg.Once = true
	cfg.Resolver = resolver
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	return poster.bodies
}

// sentRepository is the repository of one batch body.
func sentRepository(t *testing.T, body []byte) repository {
	t.Helper()
	var sent struct {
		Repository repository `json:"repository"`
	}
	if err := json.Unmarshal(body, &sent); err != nil {
		t.Fatal(err)
	}
	return sent.Repository
}

// assertKeptUnsent checks that nothing was sent, that the session is still queued with its
// events, and that the log names the session in one line and none of hidden.
func assertKeptUnsent(t *testing.T, state, dir string, bodies [][]byte, hidden ...string) {
	t.Helper()
	if len(bodies) != 0 {
		t.Fatalf("Kaya received %d batches, want none: %s", len(bodies), bodies[0])
	}
	if _, err := os.Stat(filepath.Join(dir, capture.EventsFile)); err != nil {
		t.Errorf("the unsent session left the queue: %v", err)
	}
	if _, err := os.Stat(filepath.Join(dir, cursorFile)); !errors.Is(err, os.ErrNotExist) {
		t.Errorf("the unsent session has a cursor: %v", err)
	}
	log := logText(t, state)
	var named []string
	for _, line := range strings.Split(log, "\n") {
		if strings.Contains(line, flowSession) {
			named = append(named, line)
		}
	}
	if len(named) != 1 || !strings.Contains(named[0], "not sent") {
		t.Errorf("want one not-sent line naming the session, log:\n%s", log)
	}
	for _, text := range hidden {
		if strings.Contains(log, text) {
			t.Errorf("the log names %q of an unlisted remote:\n%s", text, log)
		}
	}
}

// forkCheckout is a git checkout with the given remotes, name then url, and a queued session in
// it that recorded no remote.
func forkCheckout(t *testing.T, state string, remotes ...string) string {
	t.Helper()
	root := filepath.Join(t.TempDir(), "app")
	git := func(args ...string) {
		if out, err := exec.Command("git", args...).CombinedOutput(); err != nil {
			t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, out)
		}
	}
	git("init", "-q", root)
	for i := 0; i+1 < len(remotes); i += 2 {
		git("-C", root, "remote", "add", remotes[i], remotes[i+1])
	}
	dir := queueSession(t, state, flowSession, 1, true)
	recordSession(t, dir, "")
	summary := filepath.Join(dir, capture.SummaryFile)
	data, err := os.ReadFile(summary)
	if err != nil {
		t.Fatal(err)
	}
	writeFile(t, summary, strings.Replace(string(data), `"cwd":"/tmp"`, `"cwd":"`+filepath.Join(root, "src")+`"`, 1))
	return dir
}

func TestAnAliasedRemoteOfAnOlderReleaseIsSentAsTheListedRemote(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	recordSession(t, dir, "git@work-gh:example/app.git")
	bodies := runOnce(t, aliasResolver())
	if len(bodies) != 1 {
		t.Fatalf("Kaya received %d batches, want 1", len(bodies))
	}
	if got := sentRepository(t, bodies[0]).RemoteURL; got != "git@github.com:example/app.git" {
		t.Errorf("sent remote = %q, want the listed git@github.com:example/app.git", got)
	}
}

func TestAnAliasedRemoteThatResolvesToNoListedRemoteStaysQueued(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, true)
	recordSession(t, dir, "git@work-gh:example/private-tool.git")
	bodies := runOnce(t, aliasResolver())
	assertKeptUnsent(t, state, dir, bodies, "work-gh", "private-tool")
}

func TestAnAliasThatSSHCannotResolveIsLoggedAsSuchNotAsUnlisted(t *testing.T) {
	state := flowEnv(t)
	// Two sessions over the same alias: the resolver asks ssh about a host once per run, so the
	// second session meets the cached failure.
	const second = "11111111-aaaa-4bbb-8ccc-0000000000f2"
	sessions := map[string]string{flowSession: "git@broken-gh:example/app.git", second: "git@broken-gh:example/tool.git"}
	for id, remote := range sessions {
		dir := queueSession(t, state, id, 1, true)
		writeFile(t, filepath.Join(dir, capture.SessionFile),
			`{"sessionId":"`+id+`","machineName":"flow-host","remoteUrl":"`+remote+`","gitAuthorEmail":"dev@example.com"}`)
	}
	asked := 0
	bodies := runOnce(t, &seed.RemoteResolver{Lookup: func(host string) (string, error) {
		asked++
		return "", errors.New("ssh -G " + host + " exited 255: ssh: broken")
	}})
	if len(bodies) != 0 {
		t.Fatalf("Kaya received %d batches, want none", len(bodies))
	}
	if asked != 1 {
		t.Errorf("ssh was asked %d times, want once for the shared host", asked)
	}
	log := logText(t, state)
	for id := range sessions {
		if !strings.Contains(log, id+": not sent, "+unresolvedAlias) {
			t.Errorf("no unresolved-alias line for %s, log:\n%s", id, log)
		}
	}
	if strings.Contains(log, unlisted) {
		t.Errorf("the log calls a session whose alias ssh could not resolve unlisted:\n%s", log)
	}
	for _, text := range []string{"broken-gh", "example/", "exited 255"} {
		if strings.Contains(log, text) {
			t.Errorf("the log names %q of a remote off the list:\n%s", text, log)
		}
	}
}

func TestASessionWithoutARecordedRemoteInAForkSendsTheListedUpstream(t *testing.T) {
	state := flowEnv(t)
	forkCheckout(t, state, "origin", "https://github.com/someone/app.git", "upstream", flowRemote)
	bodies := runOnce(t, flowResolver())
	if len(bodies) != 1 {
		t.Fatalf("Kaya received %d batches, want 1", len(bodies))
	}
	repo := sentRepository(t, bodies[0])
	if repo.RemoteURL != flowRemote || repo.Name == nil || *repo.Name != "app" {
		t.Errorf("sent repository = %q named %v, want upstream's %q named app", repo.RemoteURL, repo.Name, flowRemote)
	}
}

func TestASessionWithoutARecordedRemoteInAnUnlistedCheckoutSendsNothing(t *testing.T) {
	state := flowEnv(t)
	dir := forkCheckout(t, state, "origin", "https://github.com/someone/app.git")
	bodies := runOnce(t, flowResolver())
	assertKeptUnsent(t, state, dir, bodies, "someone")
}

func TestNoCachedRepositoryListSendsNothing(t *testing.T) {
	state := flowEnv(t)
	path, err := delivery.RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	dir := queueSession(t, state, flowSession, 1, true)
	bodies := runOnce(t, flowResolver())
	assertKeptUnsent(t, state, dir, bodies, "example/app")
}

func TestLostAcknowledgementResendsAndKayaCountsEachSeqOnce(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 3, false)
	store := &ingestStore{seen: map[int64]int{}, dropFirst: true}
	server := httptest.NewServer(store)
	defer server.Close()
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
	cfg := Config{
		APIURL: server.URL, Token: func(context.Context) (string, error) { return tokenA, nil },
		Resolver: flowResolver(), Sleep: clock.Sleep, Now: clock.Now, Once: true,
		Hostname: func() (string, error) { return "flow-host", nil },
	}
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.AcknowledgedSeq != 0 {
		t.Fatalf("cursor acknowledged %d after a lost answer, want 0", cur.AcknowledgedSeq)
	}
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.AcknowledgedSeq != 3 {
		t.Fatalf("cursor acknowledged %d after the resend, want 3", cur.AcknowledgedSeq)
	}
	if store.requests != 2 {
		t.Errorf("Kaya received %d batches, want the lost one and its resend", store.requests)
	}
	// The resend carries the same events: Kaya stores each seq once, the sender sent each twice.
	for seq := int64(1); seq <= 3; seq++ {
		if store.seen[seq] != 2 {
			t.Errorf("seq %d received %d times, want 2 (the resend)", seq, store.seen[seq])
		}
	}
	if !strings.Contains(logText(t, state), "Kaya unreachable") {
		t.Errorf("log does not name the lost answer:\n%s", logText(t, state))
	}
}

func TestRedirectIsNotFollowedAndTheTokenStaysOnTheFirstHost(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	var mu sync.Mutex
	var hit []string
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		hit = append(hit, r.Header.Get("Authorization"))
		mu.Unlock()
		_, _ = w.Write([]byte(`{"success":true,"data":{"acknowledgedSeq":1}}`))
	}))
	defer target.Close()
	redirecting := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, target.URL+r.URL.Path, http.StatusTemporaryRedirect)
	}))
	defer redirecting.Close()
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
	cfg := Config{
		APIURL: redirecting.URL, Token: func(context.Context) (string, error) { return tokenA, nil },
		Resolver: flowResolver(), Sleep: clock.Sleep, Now: clock.Now, Once: true,
		Hostname: func() (string, error) { return "flow-host", nil },
	}
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	mu.Lock()
	defer mu.Unlock()
	if len(hit) != 0 {
		t.Fatalf("the redirect target received %d requests (Authorization %q); a redirect must not be followed", len(hit), hit)
	}
	if cur := (&run{}).readCursor(dir, flowSession); cur.AcknowledgedSeq != 0 {
		t.Errorf("a redirect acknowledged seq %d", cur.AcknowledgedSeq)
	}
	log := logText(t, state)
	if !strings.Contains(log, "Kaya answered 307") || strings.Contains(log, tokenA) {
		t.Errorf("log must name the 307 and never the token:\n%s", log)
	}
}

// A binary file beside the notes of a handoff directory, or a spec file that holds a NUL byte, is
// left out of the batch, counts as sent and is logged once; text files go out as before.
func TestABinarySnapshotIsSkippedAndATextOneSent(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 1, false)
	binary := map[string]string{
		// A NUL byte among the first 8000 bytes, as in an image.
		"handoffs/feature/picture.png": "\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR",
		// A NUL byte only after the first 8000 bytes, which Kaya cannot store either.
		"handoffs/feature/late.txt":        strings.Repeat("a", 9000) + "\x00b",
		"plans/files/docs/plans/broken.md": "# plan\n\x00\n",
	}
	for rel, content := range binary {
		writeFile(t, filepath.Join(dir, filepath.FromSlash(rel)), content)
	}
	writeFile(t, filepath.Join(dir, "handoffs", "feature", "notes.md"), "# notes\n")
	writeFile(t, filepath.Join(dir, "plans", "files", "docs", "plans", "spec.md"), "# spec\n")
	// The first batch fails, so the second round reads the snapshots again.
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 500}, nil}}
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 2}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if poster.calls != 2 {
		t.Fatalf("calls = %d, want the failed batch and the accepted one", poster.calls)
	}
	for i, body := range poster.bodies {
		var sent batch
		if err := json.Unmarshal(body, &sent); err != nil {
			t.Fatal(err)
		}
		var handoffs, plans []string
		for _, h := range sent.Handoffs {
			handoffs = append(handoffs, h.Path)
		}
		for _, p := range sent.Plans {
			plans = append(plans, *p.Path)
		}
		if strings.Join(handoffs, ",") != "feature/notes.md" || strings.Join(plans, ",") != "docs/plans/spec.md" {
			t.Errorf("batch %d carries handoffs %v and plans %v, want only feature/notes.md and docs/plans/spec.md", i, handoffs, plans)
		}
		if strings.Contains(string(body), `\u0000`) {
			t.Errorf("batch %d carries a NUL character", i)
		}
	}
	cur := (&run{}).readCursor(dir, flowSession)
	for _, rel := range []string{"feature/picture.png", "feature/late.txt", "feature/notes.md"} {
		if _, ok := cur.Handoffs[rel]; !ok {
			t.Errorf("cursor handoffs = %v, want %s counted as sent", cur.Handoffs, rel)
		}
	}
	for _, rel := range []string{"files/docs/plans/broken.md", "files/docs/plans/spec.md"} {
		if _, ok := cur.Plans[rel]; !ok {
			t.Errorf("cursor plans = %v, want %s counted as sent", cur.Plans, rel)
		}
	}
	log := logText(t, state)
	for _, line := range []string{
		"handoff feature/picture.png is a binary file (it holds a NUL byte), not sent",
		"handoff feature/late.txt is a binary file (it holds a NUL byte), not sent",
		"plan files/docs/plans/broken.md is a binary file (it holds a NUL byte), not sent",
	} {
		if n := strings.Count(log, flowSession+": "+line); n != 1 {
			t.Errorf("%q logged %d times over two rounds, want once, log:\n%s", line, n, log)
		}
	}
}

// A session whose batch Kaya fails must not keep the sessions after it from being sent in the
// same round.
func TestAFailingSessionDoesNotHoldBackTheSessionsAfterIt(t *testing.T) {
	state := flowEnv(t)
	first, second := flowSession, "22222222-aaaa-4bbb-8ccc-0000000000f2"
	firstDir := queueSession(t, state, first, 1, false)
	secondDir := queueSession(t, state, second, 2, false)
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
	poster := &sessionPoster{clock: clock, refuse: map[string]error{first: &client.APIError{Status: 500, Code: "GEN_INT_001"}}}
	cfg := flowConfig(clock, poster)
	cfg.Once = true
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if got := strings.Join(poster.sessions(), ","); got != first+","+second {
		t.Fatalf("sent %s, want %s and then %s", got, first, second)
	}
	if cur := (&run{}).readCursor(secondDir, second); cur.AcknowledgedSeq != 2 {
		t.Errorf("cursor of the second session = %+v, want seq 2 acknowledged", cur)
	}
	if cur := (&run{}).readCursor(firstDir, first); cur.AcknowledgedSeq != 0 || cur.Parked != nil {
		t.Errorf("cursor of the failing session = %+v, want queued and not parked", cur)
	}
	if line := first + ": Kaya answered 500 GEN_INT_001; retrying in 2 s"; !strings.Contains(logText(t, state), line) {
		t.Errorf("log lacks %q:\n%s", line, logText(t, state))
	}
}

// An answer that every session would meet alike still ends the round, also after a session whose
// own batch failed: a refused token stops the sender, and an unreachable, failing, overloaded or
// rate limiting Kaya backs it off.
func TestAnAnswerForEverySessionStillEndsTheRound(t *testing.T) {
	for name, tc := range map[string]struct {
		answer  error
		log     string
		refused bool
	}{
		"token refused":    {&client.APIError{Status: 401, Code: "KAY_SEC_003"}, "stopped: Kaya refused the token (KAY_SEC_003); set a new token", true},
		"Kaya unreachable": {errors.New("connection refused"), "Kaya unreachable: connection refused; retrying in 2 s", false},
		"gateway outage":   {&client.APIError{Status: 503}, "Kaya answered 503; retrying in 2 s", false},
		"Kaya failing":     {&client.APIError{Status: 500}, "Kaya answered 500 for 2 sessions and took no batch; retrying in 2 s", false},
		"rate limited":     {&client.APIError{Status: 429, Code: "KAY_SEC_004"}, "Kaya answered 429 KAY_SEC_004; retrying in 2 s", false},
	} {
		t.Run(name, func(t *testing.T) {
			state := flowEnv(t)
			ids := []string{flowSession, "22222222-aaaa-4bbb-8ccc-0000000000f2", "33333333-aaaa-4bbb-8ccc-0000000000f3"}
			for _, id := range ids {
				queueSession(t, state, id, 1, false)
			}
			clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
			poster := &sessionPoster{clock: clock, refuse: map[string]error{ids[0]: &client.APIError{Status: 500}, ids[1]: tc.answer}}
			cfg := flowConfig(clock, poster)
			cfg.Once = true
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if got := strings.Join(poster.sessions(), ","); got != ids[0]+","+ids[1] {
				t.Fatalf("sent %s, want %s and %s, and nothing after the answer for every session", got, ids[0], ids[1])
			}
			if !strings.Contains(logText(t, state), tc.log) {
				t.Errorf("log lacks %q:\n%s", tc.log, logText(t, state))
			}
			var saved map[string]any
			if _, err := readJSON(filepath.Join(state, capture.StateFile), &saved); err != nil {
				t.Fatal(err)
			}
			if refused := saved["token"] == tokenRefused; refused != tc.refused {
				t.Errorf("state = %v, want the token refused: %v", saved, tc.refused)
			}
		})
	}
}
