package sender

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/kaya-cli/internal/capture"
	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/seed"
)

// These tests drive the sender loop through its injected clock and sleep, with a queue built by
// hand, so the timings of the spec (back-off, idle exit, retention) are checked without waiting.

const flowSession = "11111111-aaaa-4bbb-8ccc-0000000000f1"

var errStopLoop = errors.New("test stops the loop")

// flowEnv isolates the state directory and returns it.
func flowEnv(t *testing.T) string {
	t.Helper()
	base, err := filepath.EvalSymlinks(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	for key, value := range map[string]string{
		"HOME": filepath.Join(base, "home"), "XDG_STATE_HOME": filepath.Join(base, "xdg"),
		"GIT_CONFIG_GLOBAL": os.DevNull, "GIT_CONFIG_NOSYSTEM": "1", "KAYA_SENDER": "off", "KAYA_RECORDER_TOKEN": "",
	} {
		t.Setenv(key, value)
	}
	return filepath.Join(base, "xdg", "kaya")
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
		`{"sessionId":"`+id+`","machineName":"flow-host","remoteUrl":"https://github.com/senseylabs/village.git","repositoryName":"village","gitAuthorEmail":"dev@example.com"}`)
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

func flowConfig(clock *fakeClock, poster Poster) Config {
	return Config{
		APIURL:   "https://kaya.example.com",
		Token:    func(context.Context) (string, error) { return tokenA, nil },
		Client:   func(string, string) Poster { return poster },
		Resolver: flowResolver(),
		Sleep:    clock.Sleep,
		Now:      clock.Now,
		Hostname: func() (string, error) { return "flow-host", nil },
	}
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

func TestBackoffForServerAnswersAndRateLimit(t *testing.T) {
	for _, status := range []int{429, 500, 502, 503} {
		t.Run(fmt.Sprint(status), func(t *testing.T) {
			state := flowEnv(t)
			dir := queueSession(t, state, flowSession, 1, false)
			poster := &scriptedPoster{answers: []error{&client.APIError{Status: status}}}
			clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 3}
			if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
				t.Fatalf("Run = %v", err)
			}
			if !equalDurations(clock.sleeps, seconds(2, 4, 8)) {
				t.Errorf("sleeps = %v, want 2 s, 4 s, 8 s", clock.sleeps)
			}
			if cur := (&run{}).readCursor(dir); cur.AcknowledgedSeq != 0 || cur.Parked != nil {
				t.Errorf("cursor after %d = %+v, want queued and not parked", status, cur)
			}
		})
	}
}

func TestForbiddenUnknownGitEmailBacksOffThenSendsAndKeepsTheSessionQueued(t *testing.T) {
	state := flowEnv(t)
	dir := queueSession(t, state, flowSession, 2, false)
	refusal := &client.APIError{Status: 403, Code: "KAY_SEC_007", ErrorValues: map[string]any{"gitAuthorEmail": "dev@example.com"}}
	poster := &scriptedPoster{answers: []error{refusal, refusal, nil}}
	// 2 s, 4 s while Kaya refuses, then the accepted round and the 2 s round pause.
	clock := &fakeClock{now: time.Unix(1_800_000_000, 0), limit: 3}
	if err := Run(context.Background(), flowConfig(clock, poster)); !errors.Is(err, errStopLoop) {
		t.Fatalf("Run = %v", err)
	}
	if !equalDurations(clock.sleeps, seconds(2, 4, 2)) {
		t.Errorf("sleeps = %v, want 2 s, 4 s, 2 s", clock.sleeps)
	}
	log := logText(t, state)
	if strings.Count(log, `KAY_SEC_007: no Kaya user holds the git author email "dev@example.com"`) != 2 {
		t.Errorf("log does not name the code and the email twice:\n%s", log)
	}
	if strings.Contains(log, "parked") {
		t.Errorf("a 403 KAY_SEC_007 must never park:\n%s", log)
	}
	if cur := (&run{}).readCursor(dir); cur.AcknowledgedSeq != 2 || cur.Parked != nil {
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
			poster := &scriptedPoster{answers: []error{&client.APIError{Status: status, Code: "KAY_VAL_005"}}}
			clock := &fakeClock{now: time.Unix(1_800_000_000, 0)}
			cfg := flowConfig(clock, poster)
			cfg.Once = true
			if err := Run(context.Background(), cfg); err != nil {
				t.Fatal(err)
			}
			if poster.calls != 1 {
				t.Fatalf("calls = %d, want 1", poster.calls)
			}
			if cur := (&run{}).readCursor(dir); cur.Parked == nil || cur.Parked.Status != status {
				t.Fatalf("cursor = %+v, want parked with %d", cur, status)
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
		// refuse makes Kaya answer 503, so the unacknowledged events stay queued.
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
				poster.answers = []error{&client.APIError{Status: 503}}
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
	poster := &scriptedPoster{answers: []error{&client.APIError{Status: 503}}}
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
	if cur := (&run{}).readCursor(dir); cur.AcknowledgedSeq != 0 {
		t.Fatalf("cursor acknowledged %d after a lost answer, want 0", cur.AcknowledgedSeq)
	}
	if err := Run(context.Background(), cfg); err != nil {
		t.Fatal(err)
	}
	if cur := (&run{}).readCursor(dir); cur.AcknowledgedSeq != 3 {
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
	if cur := (&run{}).readCursor(dir); cur.AcknowledgedSeq != 0 {
		t.Errorf("a redirect acknowledged seq %d", cur.AcknowledgedSeq)
	}
	log := logText(t, state)
	if !strings.Contains(log, "Kaya answered 307") || strings.Contains(log, tokenA) {
		t.Errorf("log must name the 307 and never the token:\n%s", log)
	}
}
