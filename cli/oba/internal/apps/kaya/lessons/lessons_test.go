package lessons

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

func TestSessionOf_prefersTheCallersProcessThenTheOnlyNamedSession(t *testing.T) {
	cases := []struct {
		name string
		pid  int
		live []delivery.Session
		want string
	}{
		{"own process among several", 2, []delivery.Session{{PID: 1, SessionID: "a"}, {PID: 2, SessionID: "b"}}, "b"},
		{"only named session", 9, []delivery.Session{{PID: 1, SessionID: "a"}}, "a"},
		{"launcher record does not count", 9, []delivery.Session{{PID: 1, SessionID: "a"}, {PID: 2}}, "a"},
		{"own launcher record falls through", 2, []delivery.Session{{PID: 1, SessionID: "a"}, {PID: 2}}, "a"},
		{"several and none is ours", 9, []delivery.Session{{PID: 1, SessionID: "a"}, {PID: 2, SessionID: "b"}}, ""},
		{"none", 9, nil, ""},
		{"an id Kaya would refuse", 1, []delivery.Session{{PID: 1, SessionID: "not valid"}}, ""},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := SessionOf(tc.pid, tc.live); got != tc.want {
				t.Fatalf("SessionOf = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestSplit_cutsAtHeadingsOutsideFences(t *testing.T) {
	text := "\ufeff# Lessons\r\n\r\nIntro.\r\n\r\n### First rule\r\n\r\nFirst text.\r\n" +
		"```sh\n### not a heading\n~~~\n```\nAfter the fence.\n\n" +
		"###  Second rule  \n~~~~\n### inside\n~~~\n### still inside\n~~~~\n#### A smaller heading\n" +
		"### Third rule\n"

	got := Split(text)

	want := []Block{
		{"First rule", "First text.\n```sh\n### not a heading\n~~~\n```\nAfter the fence."},
		{"Second rule", "~~~~\n### inside\n~~~\n### still inside\n~~~~\n#### A smaller heading"},
		{"Third rule", ""},
	}
	if len(got) != len(want) {
		t.Fatalf("blocks = %+v", got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("block %d = %+v, want %+v", i, got[i], want[i])
		}
	}
}

func TestSplit_aBacktickInTheInfoStringOpensNoFence(t *testing.T) {
	got := Split("### One\n``` not `a` fence\n### Two\nText.\n")
	if len(got) != 2 || got[1].Title != "Two" {
		t.Fatalf("blocks = %+v", got)
	}
}

func TestSplit_withoutHeadingsHoldsNoLesson(t *testing.T) {
	if got := Split("# Lessons\n\n## How to use\n"); len(got) != 0 {
		t.Fatalf("blocks = %+v", got)
	}
}

// memoryStore answers like Kaya: a search sees the repository's and the organization-wide
// lessons, and only finds what was added.
type memoryStore struct {
	lessons []client.Lesson
	failAdd string
	calls   int
}

func (s *memoryStore) SearchLessons(_ context.Context, _, search string) ([]client.Lesson, error) {
	var found []client.Lesson
	for _, lesson := range s.lessons {
		if strings.Contains(lesson.Title, search) {
			found = append(found, lesson)
		}
	}
	return found, nil
}

func (s *memoryStore) AddLesson(_ context.Context, request client.LessonRequest) (client.LessonCreated, error) {
	s.calls++
	if request.Title == s.failAdd {
		return client.LessonCreated{}, errors.New("refused")
	}
	repository := "demo"
	lesson := client.Lesson{Title: request.Title, Repository: &repository}
	if request.RepoWide {
		lesson.Repository = nil
	}
	s.lessons = append(s.lessons, lesson)
	return client.LessonCreated{ID: "id"}, nil
}

func TestImport_createsOnceAndCountsTheSecondRunAsSkipped(t *testing.T) {
	store := &memoryStore{}
	blocks := []Block{{"One", "text"}, {"Two", "text"}, {"One", "the same title again"}}

	first, err := Import(context.Background(), store, "remote", false, blocks)
	if err != nil || first != (Result{Created: 2, Skipped: 1}) {
		t.Fatalf("first = %+v, err = %v", first, err)
	}
	second, err := Import(context.Background(), store, "remote", false, blocks)
	if err != nil || second != (Result{Skipped: 3}) {
		t.Fatalf("second = %+v, err = %v", second, err)
	}
}

func TestImport_comparesTitlesWithinTheScope(t *testing.T) {
	store := &memoryStore{}
	blocks := []Block{{"One", "text"}}
	if _, err := Import(context.Background(), store, "remote", false, blocks); err != nil {
		t.Fatal(err)
	}

	result, err := Import(context.Background(), store, "remote", true, blocks)

	if err != nil || result != (Result{Created: 1}) {
		t.Fatalf("result = %+v, err = %v", result, err)
	}
}

func TestImport_findsATitleLongerThanASearchTakes(t *testing.T) {
	title := strings.Repeat("é", 250) + "😀"
	store := &memoryStore{}
	blocks := []Block{{title, "text"}}
	if _, err := Import(context.Background(), store, "remote", false, blocks); err != nil {
		t.Fatal(err)
	}

	result, err := Import(context.Background(), store, "remote", false, blocks)

	if err != nil || result != (Result{Skipped: 1}) {
		t.Fatalf("result = %+v, err = %v", result, err)
	}
	if got := length(searchText(title)); got != maxSearch {
		t.Fatalf("search length = %d", got)
	}
}

func TestImport_refusesTheWholeFileBeforeSending(t *testing.T) {
	store := &memoryStore{}
	blocks := []Block{{"Good", "text"}, {"Empty", ""}, {"Long", strings.Repeat("x", maxBody+1)}}

	result, err := Import(context.Background(), store, "remote", false, blocks)

	if err == nil || store.calls != 0 || result != (Result{}) {
		t.Fatalf("result = %+v, calls = %d, err = %v", result, store.calls, err)
	}
	for _, name := range []string{`lesson 2 "Empty"`, `lesson 3 "Long"`} {
		if !strings.Contains(err.Error(), name) {
			t.Fatalf("err = %v", err)
		}
	}
}

func TestImport_reportsWhatPassedBeforeACallFailed(t *testing.T) {
	store := &memoryStore{failAdd: "Two"}

	result, err := Import(context.Background(), store, "remote", false, []Block{{"One", "text"}, {"Two", "text"}, {"Three", "text"}})

	if err == nil || !strings.Contains(err.Error(), `add "Two"`) || result != (Result{Created: 1}) {
		t.Fatalf("result = %+v, err = %v", result, err)
	}
}
