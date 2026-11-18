package cmd

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/delivery"
)

const lessonsAnnouncement = "Kaya lessons new or edited since your last session here:"

// setupLessonsHook is a synced checkout whose session starts run as under kaya launch, against an
// API that serves lessons.
func setupLessonsHook(t *testing.T) (*lessonsKaya, string) {
	t.Helper()
	lk, root := setupLessons(t)
	kaya, ok := lk.delivery.(*fakeKaya)
	if !ok {
		t.Fatalf("delivery fake is %T", lk.delivery)
	}
	kaya.publish(t, 1, map[string]string{"AGENTS.md": "rules v1\n", ".agents/skills/demo/SKILL.md": "skill v1\n"}, "AGENTS.md")
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	t.Setenv(envLaunched, "1")
	t.Setenv(envCheckout, root)
	t.Setenv(envRelease, "1")
	t.Setenv(envReleaseState, "FRESH")
	previous := currentBranch
	currentBranch = func(context.Context, string) (string, error) { return "", nil }
	t.Cleanup(func() { currentBranch = previous })
	return lk, root
}

var sessionCounter int

// startSessionWithStderr runs a session start and returns the context it added and what the hook
// said on stderr. A hook error never fails the start.
func startSessionWithStderr(t *testing.T, root string) (string, string) {
	t.Helper()
	sessionCounter++
	out, err := run(t, hookEvent(t, map[string]any{"cwd": root, "session_id": fmt.Sprintf("sess-%d", sessionCounter), "source": "startup"}), "hook", "session-start")
	if err != nil {
		t.Fatalf("session start: %s, %v", out, err)
	}
	answer, stderr, _ := strings.Cut(out, "\n")
	return additionalContext(t, answer, "SessionStart"), stderr
}

func startSession(t *testing.T, root string) string {
	t.Helper()
	context, stderr := startSessionWithStderr(t, root)
	if stderr != "" {
		t.Fatalf("stderr = %q", stderr)
	}
	return context
}

func lessonsState(t *testing.T, root string) (etag, seenAt string) {
	t.Helper()
	state, err := checkoutOf(t, root).LoadState()
	if err != nil {
		t.Fatal(err)
	}
	return state.LessonsETag, state.LessonsSeenAt
}

func lessonsFile(t *testing.T, root string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(root, ".kaya", "lessons.md"))
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func lesson(id, title, updatedAt string) storedLesson {
	return storedLesson{ID: id, Title: title, UpdatedAt: updatedAt}
}

func TestLessonsHook_firstContactWritesTheFileAndStoresBothStateFieldsAndNamesNothing(t *testing.T) {
	lk, root := setupLessonsHook(t)
	// Kaya lists by creation, so the newest edit need not come first.
	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"), lesson("l2", "Pin the base image", "2026-10-03T10:00:00.5Z"), lesson("l3", "Name the branch", "2026-10-02T10:00:00Z"))

	context := startSession(t, root)

	if strings.Contains(context, lessonsAnnouncement) {
		t.Fatalf("first contact named lessons: %s", context)
	}
	file := lessonsFile(t, root)
	if file != "Derived from Kaya. Do not edit; run kaya lessons list for the text.\n\n- Use the factory\n- Pin the base image\n- Name the branch\n" {
		t.Fatalf("file = %q", file)
	}
	info, err := os.Stat(filepath.Join(root, ".kaya", "lessons.md"))
	if err != nil || info.Mode().Perm() != 0o600 {
		t.Fatalf("mode = %v, err = %v", info, err)
	}
	etag, seenAt := lessonsState(t, root)
	if etag != lk.etag || seenAt != "2026-10-03T10:00:00.5Z" {
		t.Fatalf("etag = %q, seenAt = %q", etag, seenAt)
	}
	calls := lk.recorded()
	if len(calls) != 1 || calls[0].Since != "" || calls[0].IfNoneMatch != "" || calls[0].Remote != remoteOf(t, root) {
		t.Fatalf("calls = %+v", calls)
	}
}

func TestLessonsHook_notModifiedKeepsTheFileAndTheState(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
	startSession(t, root)
	before := lessonsFile(t, root)
	etagBefore, seenBefore := lessonsState(t, root)

	context := startSession(t, root)

	if strings.Contains(context, lessonsAnnouncement) || lessonsFile(t, root) != before {
		t.Fatalf("context = %s", context)
	}
	if etag, seenAt := lessonsState(t, root); etag != etagBefore || seenAt != seenBefore {
		t.Fatalf("state moved on a 304: %q %q", etag, seenAt)
	}
	calls := lk.recorded()
	if len(calls) != 2 || calls[1].IfNoneMatch != etagBefore || calls[1].Since != seenBefore {
		t.Fatalf("calls = %+v", calls)
	}
}

func TestLessonsHook_namesOnlyTheChangedTitlesOnceAndCountsTheRest(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"), lesson("l2", "Pin the base image", "2026-10-02T10:00:00Z"), lesson("l3", "Name the branch", "2026-10-03T10:00:00Z"))
	startSession(t, root)

	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"), lesson("l2", "Pin the base image", "2026-10-02T10:00:00Z"), lesson("l3", "Name the branch", "2026-10-03T10:00:00Z"),
		lesson("l4", "Quote\nthe title", "2026-10-05T10:00:00Z"))
	context := startSession(t, root)

	want := "\n" + lessonsAnnouncement + "\n- Quote the title\nand 3 more, see kaya lessons list"
	if !strings.HasSuffix(context, want) {
		t.Fatalf("context = %q", context)
	}
	for _, old := range []string{"Use the factory", "Pin the base image", "Name the branch"} {
		if strings.Contains(context, old) {
			t.Fatalf("an unchanged title %q was named: %s", old, context)
		}
	}
	if file := lessonsFile(t, root); !strings.Contains(file, "- Quote the title\n") {
		t.Fatalf("file = %q", file)
	}
	if _, seenAt := lessonsState(t, root); seenAt != "2026-10-05T10:00:00Z" {
		t.Fatalf("seenAt = %q", seenAt)
	}

	if context := startSession(t, root); strings.Contains(context, lessonsAnnouncement) {
		t.Fatalf("the next session repeated the title: %s", context)
	}
}

func TestLessonsHook_anEditedLessonIsNamedAgain(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
	startSession(t, root)

	lk.set(lesson("l1", "Use the factory everywhere", "2026-10-04T10:00:00Z"))
	context := startSession(t, root)

	if !strings.HasSuffix(context, "\n"+lessonsAnnouncement+"\n- Use the factory everywhere") {
		t.Fatalf("context = %q", context)
	}
}

func TestLessonsHook_theSeenTimeIsTheNewestInstantAndSurvivesAnEmptyList(t *testing.T) {
	lk, root := setupLessonsHook(t)
	// As text "10:00:00Z" sorts after "10:00:00.5Z"; as instants it is half a second earlier.
	lk.set(lesson("l1", "One", "2026-10-01T10:00:00Z"), lesson("l2", "Two", "2026-10-01T10:00:00.5Z"), lesson("l3", "Three", "2026-09-01T10:00:00Z"))
	startSession(t, root)
	if _, seenAt := lessonsState(t, root); seenAt != "2026-10-01T10:00:00.5Z" {
		t.Fatalf("seenAt = %q", seenAt)
	}

	lk.set()
	startSession(t, root)

	if _, seenAt := lessonsState(t, root); seenAt != "2026-10-01T10:00:00.5Z" {
		t.Fatalf("an empty list moved seenAt to %q", seenAt)
	}
	if file := lessonsFile(t, root); strings.Contains(file, "- ") {
		t.Fatalf("file = %q", file)
	}

	lk.set(lesson("l9", "First lesson", "2026-10-02T10:00:00Z"))
	if context := startSession(t, root); !strings.HasSuffix(context, "\n- First lesson") {
		t.Fatalf("context = %q", context)
	}
}

func TestLessonsHook_aFailedFetchKeepsTheFileAndTheStateAndTheNextGoodOneCatchesUp(t *testing.T) {
	cases := []struct {
		name   string
		status int
		code   string
	}{
		{"archived repository", http.StatusUnprocessableEntity, "KAY_VAL_002"},
		{"server error", http.StatusInternalServerError, "KAY_SRV_001"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			lk, root := setupLessonsHook(t)
			lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
			startSession(t, root)
			before := lessonsFile(t, root)
			etagBefore, seenBefore := lessonsState(t, root)

			lk.mu.Lock()
			lk.failStatus, lk.failCode = tc.status, tc.code
			lk.mu.Unlock()
			lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"), lesson("l2", "Came meanwhile", "2026-10-06T10:00:00Z"))
			context, stderr := startSessionWithStderr(t, root)

			if strings.Contains(context, lessonsAnnouncement) || lessonsFile(t, root) != before || !strings.Contains(stderr, tc.code) {
				t.Fatalf("context = %s, stderr = %q", context, stderr)
			}
			if etag, seenAt := lessonsState(t, root); etag != etagBefore || seenAt != seenBefore {
				t.Fatalf("state moved on a failed fetch: %q %q", etag, seenAt)
			}

			lk.mu.Lock()
			lk.failStatus = 0
			lk.mu.Unlock()
			if context := startSession(t, root); !strings.HasSuffix(context, "\n"+lessonsAnnouncement+"\n- Came meanwhile\nand 1 more, see kaya lessons list") {
				t.Fatalf("after the failure: %q", context)
			}
		})
	}
}

func TestLessonsHook_offlineKeepsTheFileAndTheState(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
	startSession(t, root)
	before := lessonsFile(t, root)
	etagBefore, seenBefore := lessonsState(t, root)
	closed := httptest.NewServer(http.NotFoundHandler())
	t.Setenv("KAYA_API_URL", closed.URL)
	closed.Close()

	context, stderr := startSessionWithStderr(t, root)

	if !strings.Contains(stderr, "fetch the lessons") || !strings.HasPrefix(context, "Kaya config.") || strings.Contains(context, lessonsAnnouncement) || lessonsFile(t, root) != before {
		t.Fatalf("context = %s", context)
	}
	if etag, seenAt := lessonsState(t, root); etag != etagBefore || seenAt != seenBefore {
		t.Fatalf("state moved offline: %q %q", etag, seenAt)
	}
}

func TestLessonsHook_aDeletedFileIsFetchedAgainInsteadOfAnswered304(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
	startSession(t, root)
	if err := os.Remove(filepath.Join(root, ".kaya", "lessons.md")); err != nil {
		t.Fatal(err)
	}

	startSession(t, root)

	if !strings.Contains(lessonsFile(t, root), "- Use the factory\n") {
		t.Fatal("the file was not restored")
	}
	if calls := lk.recorded(); calls[len(calls)-1].IfNoneMatch != "" {
		t.Fatalf("calls = %+v", calls)
	}
}

func sessionContextOf(t *testing.T, lines []string, changes []string, update *delivery.LessonsUpdate) (string, bool) {
	t.Helper()
	var out bytes.Buffer
	_, told, err := writeSessionContext(&out, lines, changes, nil, update, "", false)
	if err != nil {
		t.Fatal(err)
	}
	return additionalContext(t, out.String(), "SessionStart"), told
}

func updateOf(changed []string, total int) *delivery.LessonsUpdate {
	return &delivery.LessonsUpdate{Lessons: make([]client.Lesson, total), Changed: changed}
}

func TestWriteSessionContext_lessonsGiveWayAtTheLimitAndAreNotStored(t *testing.T) {
	update := updateOf([]string{"Use the factory"}, 2)

	roomy, told := sessionContextOf(t, []string{"release 1"}, nil, update)
	if !told || !strings.HasSuffix(roomy, "\n"+lessonsAnnouncement+"\n- Use the factory\nand 1 more, see kaya lessons list") {
		t.Fatalf("roomy = %q, told = %v", roomy, told)
	}

	full, told := sessionContextOf(t, []string{strings.Repeat("x ", maxContext)}, nil, update)
	if told || strings.Contains(full, "Kaya lessons") || len(full) > maxContext {
		t.Fatalf("a full context kept lessons: told = %v, length = %d", told, len(full))
	}

	changes := make([]string, 0, 400)
	for i := range 400 {
		changes = append(changes, fmt.Sprintf("path/number/%04d/%s", i, strings.Repeat("p", 30)))
	}
	crowded, told := sessionContextOf(t, []string{"release 1"}, changes, update)
	if len(crowded) > maxContext || told != strings.Contains(crowded, lessonsAnnouncement) {
		t.Fatalf("crowded: told = %v, length = %d", told, len(crowded))
	}
}

func TestWriteSessionContext_withNothingChangedTheListMayBeStored(t *testing.T) {
	context, told := sessionContextOf(t, []string{strings.Repeat("x ", maxContext)}, nil, updateOf(nil, 3))
	if !told || strings.Contains(context, "Kaya lessons") {
		t.Fatalf("told = %v, context length = %d", told, len(context))
	}
	if _, told := sessionContextOf(t, []string{"release 1"}, nil, nil); told {
		t.Fatal("no update was told")
	}
}

func TestLessonsContext_namesWhatFitsAndCountsTheRest(t *testing.T) {
	long := strings.Repeat("t", 100)
	changed := []string{long + "1", long + "2", long + "3", long + "4"}
	room := len("\n"+lessonsAnnouncement) + 2*len("\n- "+long+"1") + len("\nand 8 more, see kaya lessons list") + 5

	got := lessonsContext(changed, 10, room)

	want := "\n" + lessonsAnnouncement + "\n- " + long + "1\n- " + long + "2\nand 8 more, see kaya lessons list"
	if got != want || len(got) > room {
		t.Fatalf("got = %q (length %d, room %d)", got, len(got), room)
	}
	if got := lessonsContext(changed, 4, 10000); strings.Contains(got, "more") {
		t.Fatalf("every lesson named, yet: %q", got)
	}
	if got := lessonsContext(changed, 10, 20); got != "" {
		t.Fatalf("no room for the first line: %q", got)
	}
	if got := lessonsContext(nil, 10, 10000); got != "" {
		t.Fatalf("nothing changed: %q", got)
	}
}
