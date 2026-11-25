package lessons

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

func writeFile(t *testing.T, path, content string) string {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestLessonsAdd_sendsTheTrimmedTitleAndTheBodyFileWithTheRemote(t *testing.T) {
	lk, root := kayatest.SetupLessons(t)
	body := writeFile(t, filepath.Join(t.TempDir(), "body.md"), "\n  What went wrong.\n\nWhy and the fix.  \n\n")

	out, err := run(t, "", "lessons", "add", "--title", "  Never mock the database  ", "--body-file", body)

	if err != nil || strings.TrimSpace(out) != "Lesson new-1: https://kaya.test/lessons/new-1" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	posts := lk.Created()
	if len(posts) != 1 {
		t.Fatalf("posts = %v", posts)
	}
	post := posts[0]
	if post["title"] != "Never mock the database" || post["body"] != "What went wrong.\n\nWhy and the fix." ||
		post["remoteUrl"] != kayatest.RemoteOf(t, root) || post["repoWide"] != false {
		t.Fatalf("post = %v", post)
	}
	if _, present := post["claudeSessionId"]; present {
		t.Fatalf("an unknown session reached the wire: %v", post)
	}
}

func TestLessonsAdd_repoWideAndAnExplicitSession(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	body := writeFile(t, filepath.Join(t.TempDir(), "body.md"), "text")

	_, err := run(t, "", "lessons", "add", "--title", "Rule", "--body-file", body, "--repo-wide", "--session", "sess-flag")

	post := lk.Created()
	if err != nil || len(post) != 1 || post[0]["repoWide"] != true || post[0]["claudeSessionId"] != "sess-flag" {
		t.Fatalf("posts = %v, err = %v", post, err)
	}
}

func TestLessonsAdd_refusesWhatKayaWouldRefuseWithoutACall(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
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
	if posts := lk.Created(); len(posts) != 0 {
		t.Fatalf("a refused lesson reached Kaya: %v", posts)
	}
}

// addSession runs lessons add and returns the session id the request carried, empty when absent.
func addSession(t *testing.T, lk *kayatest.LessonsKaya, args ...string) string {
	t.Helper()
	body := writeFile(t, filepath.Join(t.TempDir(), "body.md"), "text")
	before := len(lk.Created())
	if _, err := run(t, "", append([]string{"lessons", "add", "--title", "Rule", "--body-file", body}, args...)...); err != nil {
		t.Fatal(err)
	}
	posts := lk.Created()
	if len(posts) != before+1 {
		t.Fatalf("posts = %v", posts)
	}
	id, _ := posts[before]["claudeSessionId"].(string)
	return id
}

func TestLessonsAdd_sessionResolutionOrder(t *testing.T) {
	lk, root := kayatest.SetupLessons(t)
	checkout := kayatest.CheckoutOf(t, root)
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

func sampleLessons() []kayatest.StoredLesson {
	demo := "demo"
	return []kayatest.StoredLesson{
		{ID: "l1", Title: "Use the factory", Body: "Because tests share state.\nSecond line.", Repository: &demo, UpdatedAt: "2026-10-01T10:00:00Z"},
		{ID: "l2", Title: "Pin the base image", Body: "Latest tags drift.", UpdatedAt: "2026-10-02T10:00:00Z"},
	}
}

func TestLessonsList_printsTitlesAndTheRemote(t *testing.T) {
	lk, root := kayatest.SetupLessons(t)
	lk.Set(sampleLessons()...)

	out, err := run(t, "", "lessons", "list")

	if err != nil || out != "- l1 Use the factory\n- l2 Pin the base image\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	calls := lk.Recorded()
	if len(calls) != 1 || calls[0].Remote != kayatest.RemoteOf(t, root) || calls[0].Since != "" || calls[0].IfNoneMatch != "" {
		t.Fatalf("calls = %+v", calls)
	}
}

func TestLessonsList_withoutLessonsSaysSo(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	lk.Set()

	out, err := run(t, "", "lessons", "list")

	if err != nil || strings.TrimSpace(out) != "No lessons found." {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestLessonsList_jsonIsAnArrayAlsoWhenEmpty(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	lk.Set(sampleLessons()...)

	out, err := run(t, "", "lessons", "list", "--json")

	var rows []struct {
		ID, Title  string
		Repository *string
	}
	if err != nil || json.Unmarshal([]byte(out), &rows) != nil || len(rows) != 2 || rows[0].ID != "l1" ||
		rows[0].Repository == nil || *rows[0].Repository != "demo" || rows[1].Repository != nil {
		t.Fatalf("out = %q, err = %v", out, err)
	}

	lk.Set()
	out, err = run(t, "", "lessons", "list", "--json")
	if err != nil || strings.TrimSpace(out) != "[]" {
		t.Fatalf("empty: out = %q, err = %v", out, err)
	}
}

func TestLessonsList_searchPrintsTheTextAndKeepsWordsApart(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	lk.Set(sampleLessons()...)

	out, err := run(t, "", "lessons", "list", "--search", " tests share ")

	want := "- l1 Use the factory\n  Because tests share state.\n  Second line.\n\n"
	if err != nil || out != want {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	calls := lk.Recorded()
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
	lk, _ := kayatest.SetupLessons(t)
	for _, search := range []string{"   ", strings.Repeat("s", 201)} {
		if _, err := run(t, "", "lessons", "list", "--search", search); err == nil {
			t.Fatalf("search %q was accepted", search)
		}
	}
	if calls := lk.Recorded(); len(calls) != 0 {
		t.Fatalf("calls = %+v", calls)
	}
}

func TestLessonsShow_printsTheWholeLesson(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	lessons := sampleLessons()
	lessons[0].Session = "sess-1"
	lk.Set(lessons...)

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
	lk, _ := kayatest.SetupLessons(t)
	lk.Set(sampleLessons()...)

	out, err := run(t, "", "lessons", "remove", "l1")

	if err != nil || strings.TrimSpace(out) != "Lesson l1 removed." || len(lk.Deleted) != 1 || lk.Deleted[0] != "l1" || len(lk.Lessons) != 1 {
		t.Fatalf("out = %q, err = %v, deleted = %v", out, err, lk.Deleted)
	}
	if _, err := run(t, "", "lessons", "remove", "l1"); err == nil {
		t.Fatal("removing a lesson twice succeeded")
	}
}

func TestLessons_aMistypedSubcommandFails(t *testing.T) {
	kayatest.SetupLessons(t)
	if _, err := run(t, "", "lessons", "bogus"); err == nil {
		t.Fatal("oba kaya lessons bogus exited 0")
	}
}

const importFile = "# Lessons\n\nintro that is ignored\n\n" +
	"### First rule\n\nFirst text.\n\n```md\n### not a lesson\n```\n\n" +
	"### Second rule\n\nSecond text.\n\n" +
	"### Third rule\n\nThird text.\n"

func TestLessonsImport_splitsOutsideFencesAndSkipsOnTheSecondRun(t *testing.T) {
	lk, root := kayatest.SetupLessons(t)
	file := writeFile(t, filepath.Join(t.TempDir(), "lessons.md"), importFile)

	out, err := run(t, "", "lessons", "import", file)

	if err != nil || strings.TrimSpace(out) != "Created 3, skipped 0." {
		t.Fatalf("first run: %q, %v", out, err)
	}
	posts := lk.Created()
	if len(posts) != 3 || posts[0]["title"] != "First rule" || posts[1]["title"] != "Second rule" || posts[2]["title"] != "Third rule" {
		t.Fatalf("posts = %v", posts)
	}
	if body := posts[0]["body"]; body != "First text.\n\n```md\n### not a lesson\n```" {
		t.Fatalf("the fenced heading was cut: body = %q", body)
	}
	if posts[0]["remoteUrl"] != kayatest.RemoteOf(t, root) || posts[0]["repoWide"] != false {
		t.Fatalf("post = %v", posts[0])
	}

	out, err = run(t, "", "lessons", "import", file)

	if err != nil || strings.TrimSpace(out) != "Created 0, skipped 3." || len(lk.Created()) != 3 {
		t.Fatalf("second run: %q, %v, posts = %d", out, err, len(lk.Created()))
	}
}

func TestLessonsImport_repoWideIsAnotherScope(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	file := writeFile(t, filepath.Join(t.TempDir(), "lessons.md"), importFile)
	if _, err := run(t, "", "lessons", "import", file); err != nil {
		t.Fatal(err)
	}

	out, err := run(t, "", "lessons", "import", file, "--repo-wide")

	if err != nil || strings.TrimSpace(out) != "Created 3, skipped 0." || len(lk.Created()) != 6 || lk.Created()[3]["repoWide"] != true {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestLessonsImport_refusesAFileWithABadLessonBeforeAnyCall(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
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
	if posts := lk.Created(); len(posts) != 0 {
		t.Fatalf("posts = %v", posts)
	}
}

func TestLessonsList_readsEveryPage(t *testing.T) {
	lk, _ := kayatest.SetupLessons(t)
	stored := make([]kayatest.StoredLesson, 0, 250)
	for i := range 250 {
		stored = append(stored, kayatest.StoredLesson{ID: fmt.Sprintf("l%03d", i), Title: fmt.Sprintf("Lesson %03d", i), UpdatedAt: "2026-10-01T10:00:00Z"})
	}
	lk.Set(stored...)

	out, err := run(t, "", "lessons", "list", "--json")

	var rows []struct{ ID string }
	if err != nil || json.Unmarshal([]byte(out), &rows) != nil || len(rows) != 250 {
		t.Fatalf("rows = %d, err = %v", len(rows), err)
	}
	for i, row := range rows {
		if row.ID != stored[i].ID {
			t.Fatalf("row %d = %q, want %q", i, row.ID, stored[i].ID)
		}
	}
	var pages []string
	for _, call := range lk.Recorded() {
		pages = append(pages, call.Page)
	}
	if strings.Join(pages, ",") != "1,2,3" {
		t.Fatalf("pages = %v", pages)
	}

	out, err = run(t, "", "lessons", "list")
	if err != nil || strings.Count(out, "\n") != 250 || !strings.HasSuffix(out, "- l249 Lesson 249\n") {
		t.Fatalf("text holds %d lines, err = %v", strings.Count(out, "\n"), err)
	}
}
