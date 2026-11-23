package sender

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/redact"
)

// What one batch may carry. The server refuses bodies over 4 MiB; bodyBudget stays well below,
// so JSON escaping never tips a batch over.
const (
	batchEvents     = 500
	batchHandoffs   = 50
	batchPlans      = 20
	bodyBudget      = 3_500_000
	handoffLimit    = 200_000
	gitTimeout      = 5 * time.Second
	lostEvent       = "RecorderLostLine"
	sessionPlanFile = "session.md"
	sessionPlanMeta = "session.json"
	specPlanDir     = "files/"
)

// summaryKeys are the keys of summary.json the ingest contract reads; the others stay on the
// machine.
var summaryKeys = []string{"title", "session_title", "first_prompt", "started_at", "last_event_at", "ended", "cwd",
	"branch", "ticket", "features", "briefed_features", "event_count"}

var (
	configReleaseStates = map[string]bool{"FRESH": true, "CACHED": true, "STALE": true, "NONE": true, "UNVERIFIED": true}
	configDeliveredBy   = map[string]bool{"LAUNCHER": true, "HOOK": true}
	// planSources are the plan sources Kaya records for the plan of a session.
	planSources = map[string]bool{"PLAN_MODE": true, "MESSAGE": true}
	sha256Hex   = regexp.MustCompile(`^[0-9a-fA-F]{64}$`)
)

// queuedEvent is one line of events.jsonl, sent as it was written.
type queuedEvent struct {
	seq int64
	raw json.RawMessage
}

type repository struct {
	RemoteURL string  `json:"remoteUrl"`
	Name      *string `json:"name"`
}

type machine struct {
	MachineName       any `json:"machineName"`
	ClaudeCodeVersion any `json:"claudeCodeVersion"`
	Account           any `json:"account"`
}

type handoff struct {
	Path       string `json:"path"`
	Content    string `json:"content"`
	CapturedAt string `json:"capturedAt"`
}

type plan struct {
	Kind       string  `json:"kind"`
	Source     string  `json:"source"`
	Path       *string `json:"path"`
	Content    string  `json:"content"`
	CapturedAt string  `json:"capturedAt"`
}

// batch is the body of POST /kaya/ingest/batches.
type batch struct {
	SessionID  string            `json:"sessionId"`
	Repository repository        `json:"repository"`
	Machine    machine           `json:"machine"`
	Summary    map[string]any    `json:"summary"`
	Events     []json.RawMessage `json:"events"`
	Handoffs   []handoff         `json:"handoffs"`
	Plans      []plan            `json:"plans"`
	Usage      map[string]any    `json:"usage,omitempty"`
	seqs       []int64
}

// snapshot is a handoff or plan file and the modification time Kaya acknowledges it by.
type snapshot struct {
	rel     string
	mtimeNs int64
}

// carried is what a batch holds beside its events, recorded in the cursor once Kaya takes it.
type carried struct {
	handoffs []snapshot
	plans    []snapshot
	usageRaw []byte
}

// queuedEventOf is the event of one queue line; ok is false when the line is not a JSON object
// with an integer seq of at least 1.
func queuedEventOf(line []byte) (queuedEvent, bool) {
	line = bytes.TrimRight(line, "\r\n")
	if !utf8.Valid(line) {
		return queuedEvent{}, false
	}
	var event map[string]any
	if err := decodeNumbers(line, &event); err != nil || event == nil {
		return queuedEvent{}, false
	}
	number, ok := event["seq"].(json.Number)
	if !ok {
		return queuedEvent{}, false
	}
	seq, err := strconv.ParseInt(number.String(), 10, 64)
	if err != nil || seq < 1 {
		return queuedEvent{}, false
	}
	return queuedEvent{seq: seq, raw: json.RawMessage(line)}, true
}

// pendingEvents are the queued events with a seq above after, in queue order, at most limit. A
// last line without its newline is still being written and waits for the next round. A complete
// line that is no event is quarantined first, because Kaya acknowledges only up to the first
// missing seq and would never get past it.
func (s *run) pendingEvents(dir string, after int64, limit int, repaired bool) ([]queuedEvent, error) {
	file, err := os.Open(filepath.Join(dir, capture.EventsFile)) // #nosec G304 -- fixed name in the session's queue directory
	if errors.Is(err, fs.ErrNotExist) {
		// rule-10-no-op -- reason: a session directory without events.jsonl (being created by the hook, or pruned) has no events to send.
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	defer func() { _ = file.Close() }()
	reader := bufio.NewReader(file)
	var events []queuedEvent
	for {
		line, err := reader.ReadBytes('\n')
		if errors.Is(err, io.EOF) {
			return events, nil
		}
		if err != nil {
			return nil, fmt.Errorf("read %s: %w", file.Name(), err)
		}
		event, ok := queuedEventOf(line)
		if !ok {
			if !repaired {
				moved, err := s.quarantineInvalidLines(dir)
				if err != nil {
					return nil, err
				}
				if moved > 0 {
					return s.pendingEvents(dir, after, limit, true)
				}
			}
			s.log("%s: an event line is not JSON, skipped", dir)
			continue
		}
		if event.seq <= after {
			continue
		}
		events = append(events, event)
		if len(events) >= limit {
			return events, nil
		}
	}
}

// quarantineInvalidLines moves every complete line of events.jsonl that is no event to
// events.invalid.jsonl, and puts a lost event in place of each seq the moved lines leave missing
// between the events around them, so the session's seqs stay gapless and Kaya's acknowledgement
// moves past the loss. Invalid lines after the last event leave no gap: the hook numbers the next
// event after the last valid one. It holds the session's lock, which the hook takes for every
// append, and returns the number of lines moved.
func (s *run) quarantineInvalidLines(dir string) (int, error) {
	unlock, err := capture.LockSession(dir)
	if err != nil {
		return 0, err
	}
	defer unlock()
	path := filepath.Join(dir, capture.EventsFile)
	data, err := os.ReadFile(path) // #nosec G304 -- fixed name in the session's queue directory
	if errors.Is(err, fs.ErrNotExist) {
		return 0, nil
	}
	if err != nil {
		return 0, fmt.Errorf("read %s: %w", path, err)
	}
	end := bytes.LastIndexByte(data, '\n') + 1
	complete, tail := data[:end], data[end:]
	var kept, invalid [][]byte
	var lastSeq int64
	lastAt := ""
	gap := false
	for _, line := range bytes.SplitAfter(complete, []byte("\n")) {
		if len(line) == 0 {
			continue
		}
		event, ok := queuedEventOf(line)
		if !ok {
			invalid = append(invalid, line)
			gap = true
			continue
		}
		at := atOf(event.raw)
		if gap {
			fill := lastAt
			if fill == "" {
				fill = at
			}
			if fill == "" {
				fill = iso(s.now())
			}
			for seq := lastSeq + 1; seq < event.seq; seq++ {
				lost, err := encodeCompact(struct {
					Seq   int64  `json:"seq"`
					At    string `json:"at"`
					Event string `json:"event"`
				}{seq, fill, lostEvent})
				if err != nil {
					return 0, err
				}
				kept = append(kept, append(lost, '\n'))
			}
			gap = false
		}
		kept = append(kept, line)
		lastSeq = max(lastSeq, event.seq)
		if at != "" {
			lastAt = at
		}
	}
	if len(invalid) == 0 {
		return 0, nil
	}
	invalidPath := filepath.Join(dir, invalidLinesFile)
	out, err := os.OpenFile(invalidPath, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0o600) // #nosec G304 -- fixed name in the session's queue directory
	if err != nil {
		return 0, fmt.Errorf("open %s: %w", invalidPath, err)
	}
	_, writeErr := out.Write(bytes.Join(invalid, nil))
	closeErr := out.Close()
	if err := errors.Join(writeErr, closeErr); err != nil {
		return 0, fmt.Errorf("write %s: %w", invalidPath, err)
	}
	if err := writeAtomic(path, append(bytes.Join(kept, nil), tail...)); err != nil {
		return 0, err
	}
	s.log("%s: %d event lines are not JSON events, moved to %s", dir, len(invalid), invalidLinesFile)
	return len(invalid), nil
}

// atOf is the event's `at` when it is a non-empty string.
func atOf(raw json.RawMessage) string {
	var event struct {
		At any `json:"at"`
	}
	if err := json.Unmarshal(raw, &event); err != nil {
		return ""
	}
	at, _ := event.At.(string)
	return at
}

// changedSnapshots lists the files below base (paths with forward slashes) whose modification
// time is not the one Kaya acknowledged. Temporary files are skipped, and so is any file skip
// names.
func changedSnapshots(base string, sent map[string]int64, skip func(rel string) bool) ([]snapshot, error) {
	var found []snapshot
	err := filepath.WalkDir(base, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			if errors.Is(err, fs.ErrNotExist) {
				return nil
			}
			return err
		}
		if entry.IsDir() || strings.Contains(entry.Name(), ".tmp-") {
			return nil
		}
		rel, err := filepath.Rel(base, path)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		if skip != nil && skip(rel) {
			return nil
		}
		info, err := os.Stat(path)
		if err != nil {
			// rule-10-no-op -- reason: a snapshot replaced or removed while the directory is read is taken in the next round.
			return nil
		}
		mtime := info.ModTime().UnixNano()
		if previous, ok := sent[rel]; !ok || previous != mtime {
			found = append(found, snapshot{rel: rel, mtimeNs: mtime})
		}
		return nil
	})
	return found, err
}

// changedHandoffs are the handoff snapshots Kaya does not hold in this version, by path, at
// most limit.
func changedHandoffs(dir string, sent map[string]int64, limit int) ([]snapshot, error) {
	found, err := changedSnapshots(filepath.Join(dir, "handoffs"), sent, nil)
	sort.Slice(found, func(i, j int) bool { return found[i].rel < found[j].rel })
	return found[:min(len(found), limit)], err
}

// changedPlans are the plan snapshots Kaya does not hold in this version, in capture order (by
// modification time), at most limit. plans/session.json describes session.md and is no plan.
func changedPlans(dir string, sent map[string]int64, limit int) ([]snapshot, error) {
	found, err := changedSnapshots(filepath.Join(dir, "plans"), sent, func(rel string) bool { return rel == sessionPlanMeta })
	sort.Slice(found, func(i, j int) bool {
		if found[i].mtimeNs != found[j].mtimeNs {
			return found[i].mtimeNs < found[j].mtimeNs
		}
		return found[i].rel < found[j].rel
	})
	return found[:min(len(found), limit)], err
}

// readText reads a snapshot as text; bytes that are not UTF-8 become U+FFFD, one for each byte.
func readText(path string) (string, error) {
	data, err := os.ReadFile(path) // #nosec G304 -- a snapshot in the session's queue directory
	if err != nil {
		return "", err
	}
	if utf8.Valid(data) {
		return string(data), nil
	}
	var b strings.Builder
	for len(data) > 0 {
		r, size := utf8.DecodeRune(data)
		b.WriteRune(r)
		data = data[size:]
	}
	return b.String(), nil
}

// sendable redacts the whole content before it is cut to limit, so a secret that straddles the
// limit is never sent half masked.
func sendable(content string, limit int) string {
	content = redact.Text(content)
	if len(content) <= limit {
		return content
	}
	count := 0
	for i := range content {
		if count == limit {
			return content[:i]
		}
		count++
	}
	return content
}

func handoffOf(dir string, item snapshot) (handoff, error) {
	content, err := readText(filepath.Join(dir, "handoffs", filepath.FromSlash(item.rel)))
	if err != nil {
		return handoff{}, err
	}
	return handoff{Path: item.rel, Content: sendable(content, handoffLimit), CapturedAt: iso(time.Unix(0, item.mtimeNs))}, nil
}

// planOf is the plan entry of a snapshot; ok is false when the snapshot is not one Kaya accepts,
// which would make it refuse the whole batch.
func planOf(dir string, item snapshot) (entry plan, ok bool, err error) {
	base := filepath.Join(dir, "plans")
	switch {
	case item.rel == sessionPlanFile:
		var meta map[string]any
		if _, err := readJSON(filepath.Join(base, sessionPlanMeta), &meta); err != nil {
			return plan{}, false, err
		}
		source, _ := meta["source"].(string)
		if !planSources[source] {
			return plan{}, false, nil
		}
		entry = plan{Kind: "SESSION", Source: source}
	case strings.HasPrefix(item.rel, specPlanDir) && capture.ValidPlanPath(strings.TrimPrefix(item.rel, specPlanDir)):
		path := strings.TrimPrefix(item.rel, specPlanDir)
		entry = plan{Kind: "SPEC", Source: "FILE", Path: &path}
	default:
		return plan{}, false, nil
	}
	content, err := readText(filepath.Join(base, filepath.FromSlash(item.rel)))
	if err != nil {
		return plan{}, false, err
	}
	entry.Content = sendable(content, capture.PlanContentLimit)
	entry.CapturedAt = iso(time.Unix(0, item.mtimeNs))
	return entry, true, nil
}

// readUsage is the session's usage file: its bytes, and the usage to send, nil when the file
// holds no capture time or no window. A damaged file is removed; the status line writes a new one.
func (s *run) readUsage(sessionID string) ([]byte, map[string]any) {
	path := capture.UsagePath(s.stateDir, sessionID)
	raw, err := os.ReadFile(path) // #nosec G304 -- the status line's usage file in the state directory
	if errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	var data map[string]any
	if err == nil {
		err = decodeNumbers(raw, &data)
	}
	if err != nil {
		s.log("%s unreadable, removed: %v", path, err)
		if err := removeFile(path); err != nil {
			s.log("%s not removed: %v", path, err)
		}
		return nil, nil
	}
	usage := map[string]any{}
	for _, key := range []string{"capturedAt", "fiveHour", "sevenDay"} {
		if value, ok := data[key]; ok && value != nil {
			usage[key] = value
		}
	}
	if !truthy(usage["capturedAt"]) || (!truthy(usage["fiveHour"]) && !truthy(usage["sevenDay"])) {
		return raw, nil
	}
	return raw, usage
}

// truthy is JSON's value judged as the status line's optional fields are: null, false, zero and
// an empty string, list or object count as absent.
func truthy(v any) bool {
	switch value := v.(type) {
	case nil:
		return false
	case bool:
		return value
	case string:
		return value != ""
	case json.Number:
		f, err := value.Float64()
		return err != nil || f != 0
	case []any:
		return len(value) > 0
	case map[string]any:
		return len(value) > 0
	}
	return true
}

// readConfigRelease is summary.configRelease: the release `oba kaya hook session-start` reported for
// the session. Without the report no release was verified, so the state is NONE. A report that
// cannot be read is UNVERIFIED, an unknown state UNVERIFIED and an unknown delivery null: that is
// what Kaya stores for them, and sending them as read could make Kaya refuse the whole batch.
func (s *run) readConfigRelease(dir, sessionID string) map[string]any {
	report := map[string]any{"number": nil, "manifestSha256": nil, "state": "NONE", "deliveredBy": nil}
	path := filepath.Join(dir, configReleaseFile)
	var value any
	found, err := readJSON(path, &value)
	if err == nil && !found {
		return report
	}
	data, isObject := value.(map[string]any)
	if err != nil || !isObject {
		reason := "not a JSON object"
		if err != nil {
			reason = err.Error()
		}
		s.reportOnce(sessionID, "config-release", fmt.Sprintf("%s unreadable, reported as UNVERIFIED: %s", path, reason))
		report["state"] = "UNVERIFIED"
		return report
	}
	state, _ := data["state"].(string)
	if !configReleaseStates[state] {
		state = "UNVERIFIED"
	}
	report["state"] = state
	if deliveredBy, _ := data["deliveredBy"].(string); configDeliveredBy[deliveredBy] {
		report["deliveredBy"] = deliveredBy
	}
	sha, _ := data["manifestSha256"].(string)
	sha = strings.TrimSpace(sha)
	number, isNumber := data["number"].(json.Number)
	n, numberErr := strconv.ParseInt(number.String(), 10, 64)
	if state != "NONE" && isNumber && numberErr == nil && n >= 1 && sha256Hex.MatchString(sha) {
		report["number"] = n
		report["manifestSha256"] = strings.ToLower(sha)
	}
	return report
}

// repositoryOf is the session's repository. The hook records the remote that put the checkout
// on the repository list, cleaned, with an ssh host alias already resolved where the list needed
// it, and that remote is sent as recorded. Resolving it again would follow ssh config that maps
// a listed host to another one, such as github.com to ssh.github.com for ssh over port 443, and
// report a repository the list does not hold. Without a recorded remote git is asked in the
// session's directory.
func (s *run) repositoryOf(session, summary map[string]any) *repository {
	remote, _ := session["remoteUrl"].(string)
	if remote == "" {
		remote = s.originRemote(session, summary)
	}
	if remote == "" {
		return nil
	}
	name, _ := session["repositoryName"].(string)
	if name == "" {
		name = capture.RepositoryName(remote)
	}
	repo := &repository{RemoteURL: remote}
	if name != "" {
		repo.Name = &name
	}
	return repo
}

// originRemote is origin's remote in the session's directory, cleaned, for a session whose hook
// recorded none; "" when there is none. Nothing resolved this remote's ssh host alias before, so
// it is resolved here.
func (s *run) originRemote(session, summary map[string]any) string {
	cwd, _ := session["cwd"].(string)
	if cwd == "" {
		cwd, _ = summary["cwd"].(string)
	}
	where := capture.ExistingDir(cwd)
	if cwd == "" || where == "" {
		return ""
	}
	remote := capture.CleanRemote(s.git(where, "remote", "get-url", "origin"))
	if remote == "" {
		return ""
	}
	resolved, warning := s.resolver.Resolve(remote)
	if warning != "" {
		s.log("%s", warning)
	}
	return resolved
}

// git is the trimmed stdout of a git command in dir, "" when git fails. A git that cannot be
// started at all is logged.
func (s *run) git(dir string, args ...string) string {
	ctx, cancel := context.WithTimeout(context.Background(), gitTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, "git", append([]string{"-C", dir}, args...)...) // #nosec G204 -- fixed git subcommands on the session's directory
	cmd.Env = append(os.Environ(), "GIT_OPTIONAL_LOCKS=0", "LC_ALL=C")
	cmd.WaitDelay = 100 * time.Millisecond
	out, err := cmd.Output()
	var exitErr *exec.ExitError
	if err != nil && !errors.As(err, &exitErr) {
		s.log("git %s in %s failed: %v", strings.Join(args, " "), dir, err)
		return ""
	}
	if err != nil {
		// rule-10-no-op -- reason: a non-zero exit is an answer, such as a checkout without an origin remote.
		return ""
	}
	return strings.TrimSpace(string(out))
}

// buildBatch is the next batch of a session. A nil batch with an empty reason means there is
// nothing to send; with a reason, the session cannot be sent now.
func (s *run) buildBatch(dir, sessionID string, cur *cursor) (*batch, *carried, string) {
	var summary, session map[string]any
	found, err := readJSON(filepath.Join(dir, capture.SummaryFile), &summary)
	if err == nil {
		_, err = readJSON(filepath.Join(dir, capture.SessionFile), &session)
	}
	if err != nil {
		return nil, nil, "unreadable: " + err.Error()
	}
	if !found || summary == nil {
		return nil, nil, "no summary yet"
	}
	if session == nil {
		session = map[string]any{}
	}
	events, err := s.pendingEvents(dir, cur.AcknowledgedSeq, batchEvents, false)
	if err != nil {
		return nil, nil, "events unreadable: " + err.Error()
	}
	usageRaw, usage := s.readUsage(sessionID)
	if len(events) == 0 && usage == nil {
		return nil, nil, ""
	}
	repo := s.repositoryOf(session, summary)
	if repo == nil {
		return nil, nil, "no git remote for the session's directory"
	}
	what := &carried{}
	b := &batch{SessionID: sessionID, Repository: *repo, Events: []json.RawMessage{}, Handoffs: []handoff{}, Plans: []plan{}}
	for _, event := range events {
		b.Events = append(b.Events, event.raw)
		b.seqs = append(b.seqs, event.seq)
	}

	changed, err := changedHandoffs(dir, cur.Handoffs, batchHandoffs)
	if err != nil {
		s.log("%s: handoffs not listed: %v", sessionID, err)
	}
	for _, item := range changed {
		entry, err := handoffOf(dir, item)
		if err != nil {
			s.log("%s: handoff %s unreadable, skipped: %v", sessionID, item.rel, err)
			continue
		}
		// Kaya refuses an empty handoff; it counts as sent and goes out once it has content.
		if entry.Content != "" {
			b.Handoffs = append(b.Handoffs, entry)
		}
		what.handoffs = append(what.handoffs, item)
	}
	changed, err = changedPlans(dir, cur.Plans, batchPlans)
	if err != nil {
		s.log("%s: plans not listed: %v", sessionID, err)
	}
	for _, item := range changed {
		entry, ok, err := planOf(dir, item)
		if err != nil {
			s.log("%s: plan %s unreadable, skipped: %v", sessionID, item.rel, err)
			continue
		}
		if !ok {
			// Kaya would refuse the whole batch for it; it counts as sent and is never retried.
			s.reportOnce(sessionID, "plan:"+item.rel, fmt.Sprintf("plan %s is not one Kaya accepts, not sent", item.rel))
		} else if strings.TrimSpace(entry.Content) != "" {
			b.Plans = append(b.Plans, entry)
		}
		what.plans = append(what.plans, item)
	}

	machineName := session["machineName"]
	if !truthy(machineName) {
		if name, err := s.hostname(); err == nil {
			machineName = name
		} else {
			s.log("%s: no machine name: %v", sessionID, err)
			machineName = nil
		}
	}
	b.Machine = machine{MachineName: machineName, ClaudeCodeVersion: session["claudeCodeVersion"], Account: session["account"]}
	b.Summary = map[string]any{}
	for _, key := range summaryKeys {
		if value, ok := summary[key]; ok {
			b.Summary[key] = value
		}
	}
	b.Summary["model"] = session["model"]
	b.Summary["gitAuthorEmail"] = session["gitAuthorEmail"]
	b.Summary["configRelease"] = s.readConfigRelease(dir, sessionID)
	if usage != nil {
		b.Usage = usage
		what.usageRaw = usageRaw
	}
	if err := fitBudget(b, what); err != nil {
		return nil, nil, "not encodable: " + err.Error()
	}
	return b, what, ""
}

// fitBudget shrinks a batch until its body fits bodyBudget: handoffs first, then half of the
// events, plans last, because while events are left over the next batch is certain to carry the
// plans too. What is dropped is not recorded as sent.
func fitBudget(b *batch, what *carried) error {
	for {
		body, err := encodeCompact(b)
		if err != nil {
			return err
		}
		if len(body) <= bodyBudget || (len(b.Handoffs) == 0 && len(b.Plans) == 0 && len(b.Events) <= 1) {
			return nil
		}
		switch {
		case len(b.Handoffs) > 0:
			dropped := b.Handoffs[len(b.Handoffs)-1].Path
			b.Handoffs = b.Handoffs[:len(b.Handoffs)-1]
			what.handoffs = without(what.handoffs, dropped)
		case len(b.Events) > 1:
			b.Events = b.Events[:len(b.Events)/2]
			b.seqs = b.seqs[:len(b.seqs)/2]
		default:
			dropped := b.Plans[len(b.Plans)-1]
			b.Plans = b.Plans[:len(b.Plans)-1]
			key := sessionPlanFile
			if dropped.Kind != "SESSION" && dropped.Path != nil {
				key = specPlanDir + *dropped.Path
			}
			what.plans = without(what.plans, key)
		}
	}
}

func without(items []snapshot, rel string) []snapshot {
	kept := items[:0]
	for _, item := range items {
		if item.rel != rel {
			kept = append(kept, item)
		}
	}
	return kept
}
