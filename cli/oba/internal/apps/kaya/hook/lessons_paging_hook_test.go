package hook

import (
	"fmt"
	"net/http"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// manyLessons is n lessons as Kaya lists them, each edited a minute after the one before, so the
// newest edit sits on the last page.
func manyLessons(n int) []kayatest.StoredLesson {
	start := time.Date(2026, 10, 1, 10, 0, 0, 0, time.UTC)
	lessons := make([]kayatest.StoredLesson, 0, n)
	for i := range n {
		lessons = append(lessons, lesson(fmt.Sprintf("l%03d", i), fmt.Sprintf("Lesson %03d", i), start.Add(time.Duration(i)*time.Minute).Format(time.RFC3339)))
	}
	return lessons
}

// lessonsFileOf is the .kaya/lessons.md the hook writes for lessons.
func lessonsFileOf(lessons []kayatest.StoredLesson) string {
	var text strings.Builder
	text.WriteString("Derived from Kaya. Do not edit; run oba kaya lessons list for the text.\n\n")
	for _, l := range lessons {
		text.WriteString("- " + l.Title + "\n")
	}
	return text.String()
}

// onPage sets what the fake does before it answers a list page.
func onPage(lk *kayatest.LessonsKaya, do func(r *http.Request, page int)) {
	lk.Mu.Lock()
	lk.OnPage = do
	lk.Mu.Unlock()
}

func TestLessonsHook_readsEveryPageAndNamesAnEditOnTheLastPage(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lessons := manyLessons(250)
	lk.Set(lessons...)

	startSession(t, root)

	if file := lessonsFile(t, root); file != lessonsFileOf(lessons) {
		t.Fatalf("file holds %d lines, want 250 lessons", strings.Count(file, "\n- "))
	}
	etag, seenAt := lessonsState(t, root)
	if etag != lk.ETag || seenAt != lessons[249].UpdatedAt {
		t.Fatalf("etag = %q, seenAt = %q", etag, seenAt)
	}
	calls := lk.Recorded()
	if len(calls) != 3 {
		t.Fatalf("calls = %+v", calls)
	}
	for i, call := range calls {
		wantETag := lk.ETag
		if i == 0 {
			wantETag = ""
		}
		if call.Page != fmt.Sprint(i+1) || call.Amount != fmt.Sprint(client.LessonsPageSize) || call.ETag != wantETag || call.IfNoneMatch != "" {
			t.Fatalf("call %d = %+v", i, call)
		}
	}

	lessons[240].Title, lessons[240].UpdatedAt = "Edited on page three", "2026-11-01T10:00:00Z"
	lk.Set(lessons...)
	context := startSession(t, root)

	if !strings.HasSuffix(context, "\n"+lessonsAnnouncement+"\n- Edited on page three\nand 249 more, see oba kaya lessons list") {
		t.Fatalf("context = %q", context)
	}
	for _, call := range lk.Recorded()[3:] {
		if call.Since != seenAt {
			t.Fatalf("a page went without the seen time: %+v", call)
		}
	}
	if file := lessonsFile(t, root); file != lessonsFileOf(lessons) {
		t.Fatal("the edit did not reach the file")
	}
}

func TestLessonsHook_a304OnPage1SkipsEveryPage(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.Set(manyLessons(250)...)
	startSession(t, root)
	before := lessonsFile(t, root)
	etagBefore, seenBefore := lessonsState(t, root)
	asked := len(lk.Recorded())

	context := startSession(t, root)

	calls := lk.Recorded()[asked:]
	if len(calls) != 1 || calls[0].Page != "1" || calls[0].IfNoneMatch != etagBefore {
		t.Fatalf("calls = %+v", calls)
	}
	if strings.Contains(context, lessonsAnnouncement) || lessonsFile(t, root) != before || strings.Count(before, "\n- ") != 250 {
		t.Fatalf("context = %s", context)
	}
	if etag, seenAt := lessonsState(t, root); etag != etagBefore || seenAt != seenBefore {
		t.Fatalf("state moved on a 304: %q %q", etag, seenAt)
	}
}

func TestLessonsHook_aChangeBetweenPagesStartsAgainAtPage1(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lessons := manyLessons(250)
	lk.Set(lessons...)
	added := append([]kayatest.StoredLesson{lesson("new", "Added while reading", "2026-11-02T10:00:00Z")}, lessons...)
	var once sync.Once
	onPage(lk, func(_ *http.Request, page int) {
		if page == 2 {
			once.Do(func() { lk.Set(added...) })
		}
	})

	startSession(t, root)

	if file := lessonsFile(t, root); file != lessonsFileOf(added) {
		t.Fatalf("file holds %d lines, want 251 lessons", strings.Count(file, "\n- "))
	}
	if etag, _ := lessonsState(t, root); etag != lk.ETag {
		t.Fatalf("etag = %q, want the one of the lessons read, %q", etag, lk.ETag)
	}
	var pages []string
	for _, call := range lk.Recorded() {
		pages = append(pages, call.Page)
	}
	if strings.Join(pages, ",") != "1,2,1,2,3" {
		t.Fatalf("pages = %v", pages)
	}
}

func TestLessonsHook_lessonsThatKeepChangingKeepTheFileAndTheETag(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.Set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
	startSession(t, root)
	before := lessonsFile(t, root)
	etagBefore, seenBefore := lessonsState(t, root)
	lessons := manyLessons(250)
	lk.Set(lessons...)
	asked := len(lk.Recorded())
	onPage(lk, func(_ *http.Request, page int) {
		if page == 2 {
			lk.Set(lessons...)
		}
	})

	context, stderr := startSessionWithStderr(t, root)

	if !strings.Contains(stderr, "fetch the lessons") || !strings.Contains(stderr, client.CodeLessonsChanged) {
		t.Fatalf("stderr = %q", stderr)
	}
	if strings.Contains(context, lessonsAnnouncement) || lessonsFile(t, root) != before {
		t.Fatalf("context = %s", context)
	}
	if etag, seenAt := lessonsState(t, root); etag != etagBefore || seenAt != seenBefore {
		t.Fatalf("state moved on a partial read: %q %q", etag, seenAt)
	}
	if calls := lk.Recorded()[asked:]; len(calls) != 6 {
		t.Fatalf("a read of three attempts made %d calls: %+v", len(calls), calls)
	}
}

func TestLessonsHook_aBudgetThatRunsOutBetweenPagesKeepsTheFileAndTheETag(t *testing.T) {
	lk, root := setupLessonsHook(t)
	lk.Set(lesson("l1", "Use the factory", "2026-10-01T10:00:00Z"))
	startSession(t, root)
	before := lessonsFile(t, root)
	etagBefore, seenBefore := lessonsState(t, root)
	lk.Set(manyLessons(250)...)
	previous := lessonsTimeout
	lessonsTimeout = time.Second
	t.Cleanup(func() { lessonsTimeout = previous })
	onPage(lk, func(r *http.Request, page int) {
		if page == 2 {
			<-r.Context().Done()
		}
	})

	context, stderr := startSessionWithStderr(t, root)

	if !strings.Contains(stderr, "fetch the lessons: read page 2 of the lessons") || !strings.Contains(stderr, "context deadline exceeded") {
		t.Fatalf("stderr = %q", stderr)
	}
	if strings.Contains(context, lessonsAnnouncement) || lessonsFile(t, root) != before {
		t.Fatalf("context = %s", context)
	}
	if etag, seenAt := lessonsState(t, root); etag != etagBefore || seenAt != seenBefore {
		t.Fatalf("state moved on a partial read: %q %q", etag, seenAt)
	}
}
