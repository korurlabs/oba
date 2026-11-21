package delivery

import (
	"context"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// fakeFeedback is the plan feedback API of one author, in memory.
type fakeFeedback struct {
	mu       sync.Mutex
	pending  []client.PlanFeedback
	asked    [][]string
	acked    map[string][]string
	fetchErr error
	ackErr   error
}

func (f *fakeFeedback) PendingFeedback(_ context.Context, sessionIDs []string) ([]client.PlanFeedback, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.asked = append(f.asked, sessionIDs)
	if f.fetchErr != nil {
		return nil, f.fetchErr
	}
	var items []client.PlanFeedback
	for _, item := range f.pending {
		if slices.Contains(sessionIDs, item.SessionID) {
			items = append(items, item)
		}
	}
	return items, nil
}

func (f *fakeFeedback) PendingFeedbackForBranch(context.Context, string, string) ([]client.PlanFeedback, error) {
	return nil, errors.New("not used")
}

func (f *fakeFeedback) AckFeedback(_ context.Context, sessionID string, ids []string) (int, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.ackErr != nil {
		return 0, f.ackErr
	}
	if f.acked == nil {
		f.acked = map[string][]string{}
	}
	f.acked[sessionID] = append(f.acked[sessionID], ids...)
	f.pending = slices.DeleteFunc(f.pending, func(item client.PlanFeedback) bool { return slices.Contains(ids, item.ID) })
	return len(ids), nil
}

func inboxCheckout(t *testing.T) *Checkout {
	t.Helper()
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	return &Checkout{Root: root, Kind: Delivered}
}

func item(id, sessionID string) client.PlanFeedback {
	return client.PlanFeedback{ID: id, SessionID: sessionID, Kind: "SESSION", ReviewerName: "Ada", Decision: client.DecisionComment, Comment: "c " + id}
}

func ids(items []client.PlanFeedback) []string {
	out := []string{}
	for _, i := range items {
		out = append(out, i.ID)
	}
	return out
}

func TestInbox_watcherWritesHookDeliversAndAcksArePostedThenDeleted(t *testing.T) {
	ctx := context.Background()
	c := inboxCheckout(t)
	src := &fakeFeedback{pending: []client.PlanFeedback{item("f-1", "sess-1"), item("f-2", "sess-1"), item("f-3", "sess-2")}}

	if err := c.SyncFeedback(ctx, src, []string{"sess-1", "sess-2", "bad id"}); err != nil {
		t.Fatal(err)
	}
	if got, err := c.ReadInbox("sess-1"); err != nil || !slices.Equal(ids(got), []string{"f-1", "f-2"}) {
		t.Fatalf("inbox of sess-1 = %v, %v", ids(got), err)
	}
	if len(src.asked) != 1 || !slices.Equal(src.asked[0], []string{"sess-1", "sess-2"}) {
		t.Fatalf("asked = %v", src.asked)
	}

	// The hook gives the first item; the second stays for the next hook.
	err := c.DeliverInbox(ctx, "sess-1", func(items []client.PlanFeedback) (int, error) { return 1, nil })
	if err != nil {
		t.Fatal(err)
	}
	if got, _ := c.ReadInbox("sess-1"); !slices.Equal(ids(got), []string{"f-2"}) {
		t.Fatalf("inbox after delivery = %v", ids(got))
	}
	if acks, _ := c.readAcks("sess-1"); !slices.Equal(acks, []string{"f-1"}) {
		t.Fatalf("acks = %v", acks)
	}

	// Kaya still lists f-1 as pending until the ack is posted; a rewrite before that round
	// must not hand it to the session again.
	if err := c.writeInboxes(ctx, []string{"sess-1", "sess-2"}, []string{"sess-1"}, map[string][]client.PlanFeedback{"sess-1": {item("f-1", "sess-1"), item("f-2", "sess-1")}}); err != nil {
		t.Fatal(err)
	}
	if got, _ := c.ReadInbox("sess-1"); !slices.Equal(ids(got), []string{"f-2"}) {
		t.Fatalf("inbox after a stale rewrite = %v", ids(got))
	}

	// The next round posts the ack, deletes the file, and the inbox follows Kaya again.
	if err := c.SyncFeedback(ctx, src, []string{"sess-1"}); err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(src.acked["sess-1"], []string{"f-1"}) {
		t.Fatalf("posted = %v", src.acked)
	}
	if _, err := os.Stat(c.inboxAcksPath("sess-1")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("the acks file was kept: %v", err)
	}
	if got, _ := c.ReadInbox("sess-1"); !slices.Equal(ids(got), []string{"f-2"}) {
		t.Fatalf("inbox = %v", ids(got))
	}
	if _, err := os.Stat(c.inboxPath("sess-2")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("the inbox of a session that ended was kept: %v", err)
	}
}

func TestInbox_withoutAnInboxDeliverIsNotCalled(t *testing.T) {
	c := inboxCheckout(t)
	called := false
	err := c.DeliverInbox(context.Background(), "sess-1", func([]client.PlanFeedback) (int, error) {
		called = true
		return 0, nil
	})
	if err != nil || called {
		t.Fatalf("called = %v, err = %v", called, err)
	}
	if _, err := os.Stat(c.inboxDir()); !errors.Is(err, os.ErrNotExist) {
		t.Fatal("a hook without an inbox created the inbox directory")
	}
}

func TestInbox_aFailedDeliveryMovesNothing(t *testing.T) {
	ctx := context.Background()
	c := inboxCheckout(t)
	src := &fakeFeedback{pending: []client.PlanFeedback{item("f-1", "sess-1")}}
	if err := c.SyncFeedback(ctx, src, []string{"sess-1"}); err != nil {
		t.Fatal(err)
	}
	err := c.DeliverInbox(ctx, "sess-1", func([]client.PlanFeedback) (int, error) { return 1, errors.New("stdout closed") })
	if err == nil {
		t.Fatal("the failure was lost")
	}
	if got, _ := c.ReadInbox("sess-1"); !slices.Equal(ids(got), []string{"f-1"}) {
		t.Fatalf("inbox = %v", ids(got))
	}
	if acks, _ := c.readAcks("sess-1"); len(acks) != 0 {
		t.Fatalf("acks = %v", acks)
	}
}

func TestInbox_unreachedAcksStayAndAFailedFetchKeepsTheInbox(t *testing.T) {
	ctx := context.Background()
	c := inboxCheckout(t)
	src := &fakeFeedback{pending: []client.PlanFeedback{item("f-1", "sess-1")}}
	if err := c.SyncFeedback(ctx, src, []string{"sess-1"}); err != nil {
		t.Fatal(err)
	}
	if err := c.RecordFeedbackAcks(ctx, "sess-0", []string{"f-0"}); err != nil {
		t.Fatal(err)
	}
	src.ackErr = errors.New("connection refused")
	src.fetchErr = errors.New("connection refused")

	if err := c.SyncFeedback(ctx, src, []string{"sess-1"}); err == nil {
		t.Fatal("the failures were lost")
	}
	if acks, _ := c.readAcks("sess-0"); !slices.Equal(acks, []string{"f-0"}) {
		t.Fatalf("acks = %v", acks)
	}
	if got, _ := c.ReadInbox("sess-1"); !slices.Equal(ids(got), []string{"f-1"}) {
		t.Fatalf("inbox = %v", ids(got))
	}

	// A list Kaya refuses as malformed is dropped, or it would be sent forever.
	src.ackErr = &client.APIError{Status: http.StatusUnprocessableEntity, Code: "GEN_VAL_001"}
	if err := c.PostFeedbackAcks(ctx, src); err == nil {
		t.Fatal("the refusal was not reported")
	}
	if _, err := os.Stat(c.inboxAcksPath("sess-0")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("a refused acks file was kept: %v", err)
	}
}

func TestInbox_removeInboxKeepsTheAcks(t *testing.T) {
	ctx := context.Background()
	c := inboxCheckout(t)
	src := &fakeFeedback{pending: []client.PlanFeedback{item("f-1", "sess-1"), item("f-2", "sess-1")}}
	if err := c.SyncFeedback(ctx, src, []string{"sess-1"}); err != nil {
		t.Fatal(err)
	}
	if err := c.DeliverInbox(ctx, "sess-1", func([]client.PlanFeedback) (int, error) { return 1, nil }); err != nil {
		t.Fatal(err)
	}
	if err := c.RemoveInbox(ctx, "sess-1"); err != nil {
		t.Fatal(err)
	}
	if got, err := c.ReadInbox("sess-1"); err != nil || got != nil {
		t.Fatalf("inbox = %v, %v", got, err)
	}
	if err := c.PostFeedbackAcks(ctx, src); err != nil || !slices.Equal(src.acked["sess-1"], []string{"f-1"}) {
		t.Fatalf("posted = %v, %v", src.acked, err)
	}
}
