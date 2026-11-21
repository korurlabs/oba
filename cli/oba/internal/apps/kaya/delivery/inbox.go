package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"syscall"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// FeedbackSource is the part of the Kaya API that carries reviewer feedback on plans to the
// sessions that wrote them.
type FeedbackSource interface {
	PendingFeedback(ctx context.Context, sessionIDs []string) ([]client.PlanFeedback, error)
	PendingFeedbackForBranch(ctx context.Context, remoteURL, branch string) ([]client.PlanFeedback, error)
	AckFeedback(ctx context.Context, sessionID string, feedbackIDs []string) (int, error)
}

const (
	// maxFeedbackSessions is the most session ids GET /kaya/delivery/plan-feedback takes in one call.
	maxFeedbackSessions = 20
	// maxFeedbackAcks is the most feedback ids POST /kaya/delivery/plan-feedback/acks takes in one call.
	maxFeedbackAcks = 50
	// acksSuffix ends the file of a session's delivered feedback ids that wait to be posted.
	acksSuffix = ".acks.json"
)

// The inbox lives in .kaya/inbox/: <sessionId>.json holds the pending feedback the watcher
// fetched for a live session, <sessionId>.acks.json the ids a hook already gave to that session.
// A hook reads only these files, so it never waits on the network.
func (c *Checkout) inboxDir() string { return filepath.Join(c.kayaDir(), "inbox") }
func (c *Checkout) inboxPath(sessionID string) string {
	return filepath.Join(c.inboxDir(), sessionID+".json")
}
func (c *Checkout) inboxAcksPath(sessionID string) string {
	return filepath.Join(c.inboxDir(), sessionID+acksSuffix)
}

// feedbackAcks is the content of an acks file.
type feedbackAcks struct {
	FeedbackIDs []string `json:"feedbackIds"`
}

// lockInbox takes .kaya/inbox/.lock, which keeps a hook that moves feedback to the acks file
// and the watcher that rewrites the inbox from Kaya from undoing each other's work. It is held
// only for file operations, never across a call to Kaya.
func (c *Checkout) lockInbox(ctx context.Context) (func(), error) {
	if err := c.ensureKayaDir(); err != nil {
		return nil, err
	}
	dir := c.inboxDir()
	info, err := os.Lstat(dir)
	switch {
	case errors.Is(err, fs.ErrNotExist):
		if err := os.Mkdir(dir, 0o750); err != nil && !errors.Is(err, fs.ErrExist) {
			return nil, fmt.Errorf("create %s: %w", dir, err)
		}
	case err != nil:
		return nil, fmt.Errorf("inspect %s: %w", dir, err)
	case !info.IsDir():
		return nil, fmt.Errorf("%s is not a directory; remove it", dir)
	}
	path := filepath.Join(dir, ".lock")
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed path below the checkout's .kaya
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	for {
		err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB) // #nosec G115 -- a file descriptor fits an int
		if err == nil {
			return func() {
				_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN) // #nosec G115 -- a file descriptor fits an int
				_ = file.Close()
			}, nil
		}
		if !errors.Is(err, syscall.EWOULDBLOCK) {
			_ = file.Close()
			return nil, fmt.Errorf("lock %s: %w", path, err)
		}
		select {
		case <-ctx.Done():
			_ = file.Close()
			return nil, fmt.Errorf("another kaya command holds %s: %w", path, ctx.Err())
		case <-time.After(10 * time.Millisecond):
		}
	}
}

// readJSON decodes path into out; a missing file leaves out unchanged and reports false.
func readJSON(path string, out any) (bool, error) {
	data, err := os.ReadFile(path) // #nosec G304 -- a file of the checkout's own .kaya/inbox
	if errors.Is(err, fs.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("read %s: %w", path, err)
	}
	if err := json.Unmarshal(data, out); err != nil {
		return false, fmt.Errorf("%s is damaged: %w", path, err)
	}
	return true, nil
}

// writeJSONOrRemove writes value to path by rename, or removes path when empty is true.
func writeJSONOrRemove(path string, value any, empty bool) error {
	if empty {
		if err := os.Remove(path); err != nil && !errors.Is(err, fs.ErrNotExist) {
			return fmt.Errorf("remove %s: %w", path, err)
		}
		return nil
	}
	data, err := json.Marshal(value)
	if err != nil {
		return fmt.Errorf("encode %s: %w", path, err)
	}
	return writeFileAtomic(path, append(data, '\n'), 0o600)
}

func (c *Checkout) readAcks(sessionID string) ([]string, error) {
	var acks feedbackAcks
	_, err := readJSON(c.inboxAcksPath(sessionID), &acks)
	return acks.FeedbackIDs, err
}

// addAcks merges ids into the session's acks file; the caller holds the inbox lock.
func (c *Checkout) addAcks(sessionID string, ids []string) error {
	known, err := c.readAcks(sessionID)
	if err != nil {
		return err
	}
	for _, id := range ids {
		if !slices.Contains(known, id) {
			known = append(known, id)
		}
	}
	return writeJSONOrRemove(c.inboxAcksPath(sessionID), feedbackAcks{FeedbackIDs: known}, len(known) == 0)
}

// ReadInbox returns the pending feedback of a session, nil when it has no inbox.
func (c *Checkout) ReadInbox(sessionID string) ([]client.PlanFeedback, error) {
	if !ValidSessionID(sessionID) {
		return nil, fmt.Errorf("session id %q is not valid", sessionID)
	}
	var items []client.PlanFeedback
	_, err := readJSON(c.inboxPath(sessionID), &items)
	return items, err
}

// DeliverInbox hands the session's pending feedback to deliver, which gives it to the session
// and answers how many of the leading items it gave. Those move to the acks file for the watcher
// to post; the rest stay in the inbox for the next hook. Nothing moves when deliver fails, so an
// item is never marked delivered without having reached the session. Without an inbox, deliver
// is not called.
func (c *Checkout) DeliverInbox(ctx context.Context, sessionID string, deliver func([]client.PlanFeedback) (int, error)) error {
	if !ValidSessionID(sessionID) {
		return fmt.Errorf("session id %q is not valid", sessionID)
	}
	if _, err := os.Lstat(c.inboxPath(sessionID)); errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	unlock, err := c.lockInbox(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	items, err := c.ReadInbox(sessionID)
	if err != nil || len(items) == 0 {
		return err
	}
	given, err := deliver(items)
	if err != nil || given <= 0 {
		return err
	}
	given = min(given, len(items))
	ids := make([]string, 0, given)
	for _, item := range items[:given] {
		ids = append(ids, item.ID)
	}
	if err := c.addAcks(sessionID, ids); err != nil {
		return err
	}
	rest := items[given:]
	return writeJSONOrRemove(c.inboxPath(sessionID), rest, len(rest) == 0)
}

// RecordFeedbackAcks queues the ids of feedback a session received for the watcher to post, for
// a session start whose own post did not reach Kaya.
func (c *Checkout) RecordFeedbackAcks(ctx context.Context, sessionID string, ids []string) error {
	if !ValidSessionID(sessionID) {
		return fmt.Errorf("session id %q is not valid", sessionID)
	}
	unlock, err := c.lockInbox(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	return c.addAcks(sessionID, ids)
}

// RemoveInbox drops the inbox of a session that ended. Its acks file stays for the watcher: its
// feedback reached the session even though the session is gone. Feedback the session never got
// stays pending in Kaya and reaches the next session on the branch.
func (c *Checkout) RemoveInbox(ctx context.Context, sessionID string) error {
	if !ValidSessionID(sessionID) {
		return fmt.Errorf("session id %q is not valid", sessionID)
	}
	if _, err := os.Lstat(c.inboxPath(sessionID)); errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	unlock, err := c.lockInbox(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	return writeJSONOrRemove(c.inboxPath(sessionID), nil, true)
}

// SyncFeedback is the watcher's feedback round: it posts the queued acks, then fetches the
// pending feedback of the live sessions and rewrites their inboxes, leaving out what a hook
// already delivered. Posting first keeps Kaya from answering feedback this round already
// acknowledged. Inboxes of sessions that are no longer live are removed; a session whose fetch
// failed keeps its inbox as it was.
func (c *Checkout) SyncFeedback(ctx context.Context, src FeedbackSource, sessionIDs []string) error {
	var problems []error
	if err := c.PostFeedbackAcks(ctx, src); err != nil {
		problems = append(problems, err)
	}
	var live []string
	for _, id := range sessionIDs {
		if ValidSessionID(id) && !slices.Contains(live, id) {
			live = append(live, id)
		}
	}
	fetched := map[string][]client.PlanFeedback{}
	var settled []string
	for start := 0; start < len(live); start += maxFeedbackSessions {
		part := live[start:min(start+maxFeedbackSessions, len(live))]
		items, err := src.PendingFeedback(ctx, part)
		if err != nil {
			problems = append(problems, fmt.Errorf("fetch the plan feedback of %d sessions: %w", len(part), err))
			continue
		}
		for _, item := range items {
			fetched[item.SessionID] = append(fetched[item.SessionID], item)
		}
		settled = append(settled, part...)
	}
	if err := c.writeInboxes(ctx, live, settled, fetched); err != nil {
		problems = append(problems, err)
	}
	return errors.Join(problems...)
}

func (c *Checkout) writeInboxes(ctx context.Context, live, settled []string, fetched map[string][]client.PlanFeedback) error {
	unlock, err := c.lockInbox(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	var problems []error
	for _, id := range settled {
		acked, err := c.readAcks(id)
		if err != nil {
			problems = append(problems, err)
			continue
		}
		items := slices.DeleteFunc(fetched[id], func(item client.PlanFeedback) bool { return slices.Contains(acked, item.ID) })
		if err := writeJSONOrRemove(c.inboxPath(id), items, len(items) == 0); err != nil {
			problems = append(problems, err)
		}
	}
	entries, err := os.ReadDir(c.inboxDir())
	if err != nil {
		return errors.Join(append(problems, fmt.Errorf("read %s: %w", c.inboxDir(), err))...)
	}
	for _, entry := range entries {
		name := entry.Name()
		id := strings.TrimSuffix(name, ".json")
		if strings.HasPrefix(name, ".") || strings.HasSuffix(name, acksSuffix) || id == name || slices.Contains(live, id) {
			continue
		}
		if err := writeJSONOrRemove(filepath.Join(c.inboxDir(), name), nil, true); err != nil {
			problems = append(problems, err)
		}
	}
	return errors.Join(problems...)
}

// PostFeedbackAcks posts every queued acks file, at most maxFeedbackAcks ids per call, and
// deletes each id once Kaya answered 2xx. Ids a hook queued during the call stay for the next
// round. A list Kaya refuses as malformed (400 or 422) would be refused forever, so it is
// dropped and reported.
func (c *Checkout) PostFeedbackAcks(ctx context.Context, src FeedbackSource) error {
	entries, err := os.ReadDir(c.inboxDir())
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("read %s: %w", c.inboxDir(), err)
	}
	var problems []error
	for _, entry := range entries {
		sessionID, ok := strings.CutSuffix(entry.Name(), acksSuffix)
		if !ok || !ValidSessionID(sessionID) {
			continue
		}
		if err := c.postSessionAcks(ctx, src, sessionID); err != nil {
			problems = append(problems, err)
		}
	}
	return errors.Join(problems...)
}

func (c *Checkout) postSessionAcks(ctx context.Context, src FeedbackSource, sessionID string) error {
	unlock, err := c.lockInbox(ctx)
	if err != nil {
		return err
	}
	ids, err := c.readAcks(sessionID)
	unlock()
	if err != nil || len(ids) == 0 {
		return err
	}
	var done []string
	var problems []error
	for start := 0; start < len(ids); start += maxFeedbackAcks {
		part := ids[start:min(start+maxFeedbackAcks, len(ids))]
		_, err := src.AckFeedback(ctx, sessionID, part)
		var apiErr *client.APIError
		switch {
		case err == nil:
			done = append(done, part...)
		case errors.As(err, &apiErr) && (apiErr.Status == http.StatusBadRequest || apiErr.Status == http.StatusUnprocessableEntity):
			done = append(done, part...)
			problems = append(problems, fmt.Errorf("the plan feedback acks of session %s were refused and are dropped: %w", sessionID, err))
		default:
			problems = append(problems, fmt.Errorf("post the plan feedback acks of session %s: %w", sessionID, err))
		}
	}
	if len(done) == 0 {
		return errors.Join(problems...)
	}
	unlock, err = c.lockInbox(ctx)
	if err != nil {
		return errors.Join(append(problems, err)...)
	}
	defer unlock()
	current, err := c.readAcks(sessionID)
	if err != nil {
		return errors.Join(append(problems, err)...)
	}
	left := slices.DeleteFunc(current, func(id string) bool { return slices.Contains(done, id) })
	if err := writeJSONOrRemove(c.inboxAcksPath(sessionID), feedbackAcks{FeedbackIDs: left}, len(left) == 0); err != nil {
		problems = append(problems, err)
	}
	return errors.Join(problems...)
}
