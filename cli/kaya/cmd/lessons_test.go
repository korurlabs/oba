package cmd

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

// storedLesson is a lesson as lessonsKaya keeps it.
type storedLesson struct {
	ID, Title, Body string
	Repository      *string
	UpdatedAt       string
	Session         string
}

// lessonsCall is one request to the lessons list.
type lessonsCall struct {
	Remote, Since, IfNoneMatch, Search string
}

// lessonsKaya serves /delivery/lessons like Kaya: a list with an ETag that answers 304, a search
// with the text, one lesson, create and delete. Everything else goes to the delivery fake.
type lessonsKaya struct {
	delivery http.Handler
	mu       sync.Mutex
	lessons  []storedLesson
	etag     string
	version  int
	// failStatus and failCode make the conditional list answer an error.
	failStatus int
	failCode   string
	calls      []lessonsCall
	posts      []map[string]any
	deleted    []string
}

func (k *lessonsKaya) set(lessons ...storedLesson) {
	k.mu.Lock()
	defer k.mu.Unlock()
	k.lessons = lessons
	k.version++
	k.etag = fmt.Sprintf(`"v%d"`, k.version)
}

func (k *lessonsKaya) recorded() []lessonsCall {
	k.mu.Lock()
	defer k.mu.Unlock()
	return append([]lessonsCall(nil), k.calls...)
}

func (k *lessonsKaya) created() []map[string]any {
	k.mu.Lock()
	defer k.mu.Unlock()
	return append([]map[string]any(nil), k.posts...)
}

func lessonRow(l storedLesson, since string, withBody bool) map[string]any {
	changed := false
	if since != "" {
		sinceTime, errSince := time.Parse(time.RFC3339, since)
		updated, errUpdated := time.Parse(time.RFC3339, l.UpdatedAt)
		changed = errSince == nil && errUpdated == nil && updated.After(sinceTime)
	}
	row := map[string]any{"id": l.ID, "title": l.Title, "repository": l.Repository, "changed": changed, "updatedAt": l.UpdatedAt}
	if withBody {
		row["body"] = l.Body
	}
	return row
}

func (k *lessonsKaya) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/kaya/delivery/lessons" && !strings.HasPrefix(r.URL.Path, "/kaya/delivery/lessons/") {
		k.delivery.ServeHTTP(w, r)
		return
	}
	k.mu.Lock()
	defer k.mu.Unlock()
	reply := func(status int, value any) {
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": value})
	}
	fail := func(status int, code string) {
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(map[string]any{"success": false, "error": map[string]any{"code": code, "message": "refused"}})
	}
	query := r.URL.Query()
	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/kaya/delivery/lessons":
		k.calls = append(k.calls, lessonsCall{query.Get("remoteUrl"), query.Get("since"), r.Header.Get("If-None-Match"), ""})
		if k.failStatus != 0 {
			fail(k.failStatus, k.failCode)
			return
		}
		if match := r.Header.Get("If-None-Match"); match != "" && match == k.etag {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		rows := []map[string]any{}
		for _, l := range k.lessons {
			rows = append(rows, lessonRow(l, query.Get("since"), false))
		}
		w.Header().Set("ETag", k.etag)
		reply(http.StatusOK, rows)
	case r.Method == http.MethodGet && r.URL.Path == "/kaya/delivery/lessons/search":
		search := query.Get("search")
		k.calls = append(k.calls, lessonsCall{Remote: query.Get("remoteUrl"), Search: search})
		rows := []map[string]any{}
		for _, l := range k.lessons {
			if strings.Contains(strings.ToLower(l.Title+"\n"+l.Body), strings.ToLower(search)) {
				rows = append(rows, lessonRow(l, "", true))
			}
		}
		reply(http.StatusOK, rows)
	case r.Method == http.MethodPost && r.URL.Path == "/kaya/delivery/lessons":
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		k.posts = append(k.posts, body)
		lesson := storedLesson{ID: fmt.Sprintf("new-%d", len(k.posts)), UpdatedAt: "2026-10-20T10:00:00Z"}
		lesson.Title, _ = body["title"].(string)
		lesson.Body, _ = body["body"].(string)
		if wide, _ := body["repoWide"].(bool); !wide {
			repository := "demo"
			lesson.Repository = &repository
		}
		k.lessons = append(k.lessons, lesson)
		reply(http.StatusCreated, map[string]any{"id": lesson.ID, "url": "https://kaya.test/lessons/" + lesson.ID})
	case r.Method == http.MethodGet:
		id := strings.TrimPrefix(r.URL.Path, "/kaya/delivery/lessons/")
		for _, l := range k.lessons {
			if l.ID != id {
				continue
			}
			detail := map[string]any{
				"id": l.ID, "title": l.Title, "body": l.Body, "user": map[string]any{"id": "u1", "name": "Ada"},
				"claudeSessionId": l.Session, "updatedAt": l.UpdatedAt, "createdAt": l.UpdatedAt,
			}
			if l.Repository != nil {
				detail["repository"] = map[string]any{"id": "r1", "name": *l.Repository}
			}
			reply(http.StatusOK, detail)
			return
		}
		fail(http.StatusNotFound, "KAY_NF_001")
	case r.Method == http.MethodDelete:
		id := strings.TrimPrefix(r.URL.Path, "/kaya/delivery/lessons/")
		for i, l := range k.lessons {
			if l.ID == id {
				k.lessons = append(k.lessons[:i], k.lessons[i+1:]...)
				k.deleted = append(k.deleted, id)
				reply(http.StatusOK, nil)
				return
			}
		}
		fail(http.StatusNotFound, "KAY_NF_001")
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

// setupLessons is a delivered checkout whose API also serves lessons.
func setupLessons(t *testing.T) (*lessonsKaya, string) {
	t.Helper()
	kaya, root := setupDelivery(t)
	lk := &lessonsKaya{delivery: kaya}
	server := httptest.NewServer(lk)
	t.Cleanup(server.Close)
	t.Setenv("KAYA_API_URL", server.URL)
	return lk, root
}

func checkoutOf(t *testing.T, root string) *delivery.Checkout {
	t.Helper()
	checkout, err := delivery.FindCheckout(root)
	if err != nil {
		t.Fatal(err)
	}
	return checkout
}

func remoteOf(t *testing.T, root string) string {
	t.Helper()
	remote, _, err := checkoutOf(t, root).Remotes()
	if err != nil {
		t.Fatal(err)
	}
	return remote
}

func writeFile(t *testing.T, path, content string) string {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestLessonsAdd_sendsTheTrimmedTitleAndTheBodyFileWithTheRemote(t *testing.T) {
	lk, root := setupLessons(t)
	body := writeFile(t, filepath.Join(t.TempDir(), "body.md"), "\n  What went wrong.\n\nWhy and the fix.  \n\n")

	out, err := run(t, "", "lessons", "add", "--title", "  Never mock the database  ", "--body-file", body)

	if err != nil || strings.TrimSpace(out) != "Lesson new-1: https://kaya.test/lessons/new-1" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	posts := lk.created()
	if len(posts) != 1 {
		t.Fatalf("posts = %v", posts)
	}
	post := posts[0]
	if post["title"] != "Never mock the database" || post["body"] != "What went wrong.\n\nWhy and the fix." ||
		post["remoteUrl"] != remoteOf(t, root) || post["repoWide"] != false {
		t.Fatalf("post = %v", post)
	}
	if _, present := post["claudeSessionId"]; present {
		t.Fatalf("an unknown session reached the wire: %v", post)
	}
}

func TestLessonsAdd_repoWideAndAnExplicitSession(t *testing.T) {
	lk, _ := setupLessons(t)
	body := writeFile(t, filepath.Join(t.TempDir(), "body.md"), "text")

	_, err := run(t, "", "lessons", "add", "--title", "Rule", "--body-file", body, "--repo-wide", "--session", "sess-flag")

	post := lk.created()
	if err != nil || len(post) != 1 || post[0]["repoWide"] != true || post[0]["claudeSessionId"] != "sess-flag" {
		t.Fatalf("posts = %v, err = %v", post, err)
	}
}

func TestLessonsAdd_refusesWhatKayaWouldRefuseWithoutACall(t *testing.T) {
	lk, _ := setupLessons(t)
	dir := t.TempDir()
	good := writeFile(t, filepath.Join(dir, "good.md"), "text")
	empty := writeFile(t, filepath.Join(dir, "empty.md"), " \n")
	long := writeFile(t, filepath.Join(dir, "long.md"), strings.Repeat("x", 4001))
	cases := []struct {
		name string
		args []string
		want string
	}{
		{"empty title", []string{"--title", "  ", "--body-file", good}, "title is empty"},
		{"title with a line break", []string{"--title", "a\nb", "--body-file", good}, "spans lines"},
		{"title over 300", []string{"--title", strings.Repeat("t", 301), "--body-file", good}, "at most 300"},
		{"no body file", []string{"--title", "Rule"}, "--body-file"},
		{"missing body file", []string{"--title", "Rule", "--body-file", filepath.Join(dir, "none.md")}, "read the lesson text"},
		{"empty body", []string{"--title", "Rule", "--body-file", empty}, "text is empty"},
		{"body over 4000", []string{"--title", "Rule", "--body-file", long}, "at most 4000"},
		{"bad session", []string{"--title", "Rule", "--body-file", good, "--session", "not valid"}, "not a Claude Code session id"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := run(t, "", append([]string{"lessons", "add"}, tc.args...)...)
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("err = %v, want %q", err, tc.want)
			}
		})
	}
	if posts := lk.created(); len(posts) != 0 {
		t.Fatalf("a refused lesson reached Kaya: %v", posts)
	}
}

// addSession runs lessons add and returns the session id the request carried, empty when absent.
func addSession(t *testing.T, lk *lessonsKaya, args ...string) string {
	t.Helper()
	body := writeFile(t, filepath.Join(t.TempDir(), "body.md"), "text")
	before := len(lk.created())
	if _, err := run(t, "", append([]string{"lessons", "add", "--title", "Rule", "--body-file", body}, args...)...); err != nil {
		t.Fatal(err)
	}
	posts := lk.created()
	if len(posts) != before+1 {
		t.Fatalf("posts = %v", posts)
	}
	id, _ := posts[before]["claudeSessionId"].(string)
	return id
}

func TestLessonsAdd_sessionResolutionOrder(t *testing.T) {
	lk, root := setupLessons(t)
	checkout := checkoutOf(t, root)
	other := exec.Command("sleep", "60")
	if err := other.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = other.Process.Kill(); _ = other.Wait() })

	if got := addSession(t, lk); got != "" {
		t.Fatalf("no session known: got %q", got)
	}

	if err := checkout.RegisterSession(delivery.Session{PID: os.Getpid(), SessionID: "sess-solo"}); err != nil {
		t.Fatal(err)
	}
	if got := addSession(t, lk); got != "sess-solo" {
		t.Fatalf("one live session: got %q", got)
	}

	if err := checkout.RegisterSession(delivery.Session{PID: other.Process.Pid, SessionID: "sess-other"}); err != nil {
		t.Fatal(err)
	}
	if got := addSession(t, lk); got != "" {
		t.Fatalf("two live sessions, none ours: got %q", got)
	}

	if err := checkout.RegisterSession(delivery.Session{PID: delivery.ClaudePID(), SessionID: "sess-mine"}); err != nil {
		t.Fatal(err)
	}
	if got := addSession(t, lk); got != "sess-mine" {
		t.Fatalf("the record of the caller's process: got %q", got)
	}

	if got := addSession(t, lk, "--session", "sess-flag"); got != "sess-flag" {
		t.Fatalf("the flag: got %q", got)
	}
}

func sampleLessons() []storedLesson {
	demo := "demo"
	return []storedLesson{
		{ID: "l1", Title: "Use the factory", Body: "Because tests share state.\nSecond line.", Repository: &demo, UpdatedAt: "2026-10-01T10:00:00Z"},
		{ID: "l2", Title: "Pin the base image", Body: "Latest tags drift.", UpdatedAt: "2026-10-02T10:00:00Z"},
	}
}

func TestLessonsList_printsTitlesAndTheRemote(t *testing.T) {
	lk, root := setupLessons(t)
	lk.set(sampleLessons()...)

	out, err := run(t, "", "lessons", "list")

	if err != nil || out != "- l1 Use the factory\n- l2 Pin the base image\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	calls := lk.recorded()
	if len(calls) != 1 || calls[0].Remote != remoteOf(t, root) || calls[0].Since != "" || calls[0].IfNoneMatch != "" {
		t.Fatalf("calls = %+v", calls)
	}
}

func TestLessonsList_withoutLessonsSaysSo(t *testing.T) {
	lk, _ := setupLessons(t)
	lk.set()

	out, err := run(t, "", "lessons", "list")

	if err != nil || strings.TrimSpace(out) != "No lessons found." {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestLessonsList_jsonIsAnArrayAlsoWhenEmpty(t *testing.T) {
	lk, _ := setupLessons(t)
	lk.set(sampleLessons()...)

	out, err := run(t, "", "lessons", "list", "--json")

	var rows []struct {
		ID, Title  string
		Repository *string
	}
	if err != nil || json.Unmarshal([]byte(out), &rows) != nil || len(rows) != 2 || rows[0].ID != "l1" ||
		rows[0].Repository == nil || *rows[0].Repository != "demo" || rows[1].Repository != nil {
		t.Fatalf("out = %q, err = %v", out, err)
	}

	lk.set()
	out, err = run(t, "", "lessons", "list", "--json")
	if err != nil || strings.TrimSpace(out) != "[]" {
		t.Fatalf("empty: out = %q, err = %v", out, err)
	}
}

func TestLessonsList_searchPrintsTheTextAndKeepsWordsApart(t *testing.T) {
	lk, _ := setupLessons(t)
	lk.set(sampleLessons()...)

	out, err := run(t, "", "lessons", "list", "--search", " tests share ")

	want := "- l1 Use the factory\n  Because tests share state.\n  Second line.\n\n"
	if err != nil || out != want {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	calls := lk.recorded()
	if len(calls) != 1 || calls[0].Search != "tests share" {
		t.Fatalf("calls = %+v", calls)
	}

	out, err = run(t, "", "lessons", "list", "--search", "nothing like it", "--json")
	if err != nil || strings.TrimSpace(out) != "[]" {
		t.Fatalf("no hit: out = %q, err = %v", out, err)
	}

	// A later run without the flag lists everything again.
	out, err = run(t, "", "lessons", "list")
	if err != nil || !strings.Contains(out, "- l2 Pin the base image") || strings.Contains(out, "Latest tags") {
		t.Fatalf("plain list after a search: %q, %v", out, err)
	}
}

func TestLessonsList_refusesAnEmptyOrTooLongSearch(t *testing.T) {
	lk, _ := setupLessons(t)
	for _, search := range []string{"   ", strings.Repeat("s", 201)} {
		if _, err := run(t, "", "lessons", "list", "--search", search); err == nil {
			t.Fatalf("search %q was accepted", search)
		}
	}
	if calls := lk.recorded(); len(calls) != 0 {
		t.Fatalf("calls = %+v", calls)
	}
}

func TestLessonsShow_printsTheWholeLesson(t *testing.T) {
	lk, _ := setupLessons(t)
	lessons := sampleLessons()
	lessons[0].Session = "sess-1"
	lk.set(lessons...)

	out, err := run(t, "", "lessons", "show", "l1")
	want := "Use the factory\n\nRepository: demo\nAuthor: Ada\nUpdated: 2026-10-01T10:00:00Z\nSession: sess-1\n\nBecause tests share state.\nSecond line.\n"
	if err != nil || out != want {
		t.Fatalf("out = %q, err = %v", out, err)
	}

	out, err = run(t, "", "lessons", "show", "l2")
	if err != nil || !strings.Contains(out, "Repository: Organization-wide") || strings.Contains(out, "Session:") {
		t.Fatalf("organization-wide: %q, %v", out, err)
	}

	if _, err := run(t, "", "lessons", "show", "missing"); err == nil || !strings.Contains(err.Error(), "KAY_NF_001") && !strings.Contains(err.Error(), "404") {
		t.Fatalf("unknown id: err = %v", err)
	}
	if _, err := run(t, "", "lessons", "show"); err == nil {
		t.Fatal("show without an id succeeded")
	}
}

func TestLessonsRemove_deletesOneLesson(t *testing.T) {
	lk, _ := setupLessons(t)
	lk.set(sampleLessons()...)

	out, err := run(t, "", "lessons", "remove", "l1")

	if err != nil || strings.TrimSpace(out) != "Lesson l1 removed." || len(lk.deleted) != 1 || lk.deleted[0] != "l1" || len(lk.lessons) != 1 {
		t.Fatalf("out = %q, err = %v, deleted = %v", out, err, lk.deleted)
	}
	if _, err := run(t, "", "lessons", "remove", "l1"); err == nil {
		t.Fatal("removing a lesson twice succeeded")
	}
}

func TestLessons_aMistypedSubcommandFails(t *testing.T) {
	setupLessons(t)
	if _, err := run(t, "", "lessons", "bogus"); err == nil {
		t.Fatal("kaya lessons bogus exited 0")
	}
}

const importFile = "# Lessons\n\nintro that is ignored\n\n" +
	"### First rule\n\nFirst text.\n\n```md\n### not a lesson\n```\n\n" +
	"### Second rule\n\nSecond text.\n\n" +
	"### Third rule\n\nThird text.\n"

func TestLessonsImport_splitsOutsideFencesAndSkipsOnTheSecondRun(t *testing.T) {
	lk, root := setupLessons(t)
	file := writeFile(t, filepath.Join(t.TempDir(), "lessons.md"), importFile)

	out, err := run(t, "", "lessons", "import", file)

	if err != nil || strings.TrimSpace(out) != "Created 3, skipped 0." {
		t.Fatalf("first run: %q, %v", out, err)
	}
	posts := lk.created()
	if len(posts) != 3 || posts[0]["title"] != "First rule" || posts[1]["title"] != "Second rule" || posts[2]["title"] != "Third rule" {
		t.Fatalf("posts = %v", posts)
	}
	if body := posts[0]["body"]; body != "First text.\n\n```md\n### not a lesson\n```" {
		t.Fatalf("the fenced heading was cut: body = %q", body)
	}
	if posts[0]["remoteUrl"] != remoteOf(t, root) || posts[0]["repoWide"] != false {
		t.Fatalf("post = %v", posts[0])
	}

	out, err = run(t, "", "lessons", "import", file)

	if err != nil || strings.TrimSpace(out) != "Created 0, skipped 3." || len(lk.created()) != 3 {
		t.Fatalf("second run: %q, %v, posts = %d", out, err, len(lk.created()))
	}
}

func TestLessonsImport_repoWideIsAnotherScope(t *testing.T) {
	lk, _ := setupLessons(t)
	file := writeFile(t, filepath.Join(t.TempDir(), "lessons.md"), importFile)
	if _, err := run(t, "", "lessons", "import", file); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, "", "lessons", "import", file, "--repo-wide")

	if err != nil || strings.TrimSpace(out) != "Created 3, skipped 0." || len(lk.created()) != 6 || lk.created()[3]["repoWide"] != true {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestLessonsImport_refusesAFileWithABadLessonBeforeAnyCall(t *testing.T) {
	lk, _ := setupLessons(t)
	dir := t.TempDir()
	bad := writeFile(t, filepath.Join(dir, "bad.md"), "### Good\n\ntext\n\n### Empty\n\n### Long\n\n"+strings.Repeat("x", 4001)+"\n")
	none := writeFile(t, filepath.Join(dir, "none.md"), "# Only a title\n\n## and a section\n")

	_, err := run(t, "", "lessons", "import", bad)
	if err == nil || !strings.Contains(err.Error(), `"Empty"`) || !strings.Contains(err.Error(), `"Long"`) {
		t.Fatalf("err = %v", err)
	}
	if _, err := run(t, "", "lessons", "import", none); err == nil || !strings.Contains(err.Error(), "holds no lesson") {
		t.Fatalf("err = %v", err)
	}
	if _, err := run(t, "", "lessons", "import", filepath.Join(dir, "absent.md")); err == nil {
		t.Fatal("a missing file was accepted")
	}
	if posts := lk.created(); len(posts) != 0 {
		t.Fatalf("posts = %v", posts)
	}
}
