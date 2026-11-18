package delivery

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// fakeLessons is a LessonSource that answers a prepared list and records what it was asked.
type fakeLessons struct {
	lessons     []client.Lesson
	etag        string
	notModified bool
	err         error
	asked       []string
}

func (f *fakeLessons) LessonsSince(_ context.Context, remote, etag, since string) ([]client.Lesson, string, bool, error) {
	f.asked = append(f.asked, remote+"|"+etag+"|"+since)
	if f.err != nil {
		return nil, "", false, f.err
	}
	if f.notModified {
		return nil, etag, true, nil
	}
	return f.lessons, f.etag, false, nil
}

func lessonAt(title, updatedAt string, changed bool) client.Lesson {
	return client.Lesson{ID: title, Title: title, UpdatedAt: updatedAt, Changed: changed}
}

func TestFetchLessons_askedConditionallyAndNothingWrittenOn304(t *testing.T) {
	f := newFixture(t)
	if err := f.d.Checkout.WriteLessons(nil); err != nil {
		t.Fatal(err)
	}
	source := &fakeLessons{notModified: true}

	update, err := f.d.Checkout.FetchLessons(context.Background(), source, "remote", State{LessonsETag: `"e1"`, LessonsSeenAt: "2026-10-01T10:00:00Z"})

	if err != nil || update != nil {
		t.Fatalf("update = %+v, err = %v", update, err)
	}
	if len(source.asked) != 1 || source.asked[0] != `remote|"e1"|2026-10-01T10:00:00Z` {
		t.Fatalf("asked = %v", source.asked)
	}
}

func TestFetchLessons_aMissingFileSendsNoETagButStillTheSeenTime(t *testing.T) {
	f := newFixture(t)
	source := &fakeLessons{lessons: []client.Lesson{lessonAt("One", "2026-10-02T10:00:00Z", false)}, etag: `"e2"`}

	update, err := f.d.Checkout.FetchLessons(context.Background(), source, "remote", State{LessonsETag: `"e1"`, LessonsSeenAt: "2026-10-01T10:00:00Z"})

	if err != nil || update == nil || update.ETag != `"e2"` {
		t.Fatalf("update = %+v, err = %v", update, err)
	}
	if source.asked[0] != "remote||2026-10-01T10:00:00Z" {
		t.Fatalf("asked = %v", source.asked)
	}
}

func TestFetchLessons_seenAtIsTheNewestInstantAndNeverMovesBack(t *testing.T) {
	f := newFixture(t)
	cases := []struct {
		name    string
		stored  string
		updated []string
		want    string
	}{
		{"newest is not first", "", []string{"2026-10-01T10:00:00Z", "2026-10-03T10:00:00Z", "2026-10-02T10:00:00Z"}, "2026-10-03T10:00:00Z"},
		{"fractions compare as instants", "", []string{"2026-10-01T10:00:00Z", "2026-10-01T10:00:00.5Z"}, "2026-10-01T10:00:00.5Z"},
		{"fractions of different length", "", []string{"2026-10-01T10:00:00.25Z", "2026-10-01T10:00:00.250001Z", "2026-10-01T10:00:00.2Z"}, "2026-10-01T10:00:00.250001Z"},
		{"empty list keeps the stored time", "2026-10-05T10:00:00Z", nil, "2026-10-05T10:00:00Z"},
		{"older rows keep the stored time", "2026-10-05T10:00:00Z", []string{"2026-10-01T10:00:00Z"}, "2026-10-05T10:00:00Z"},
		{"empty list and nothing stored", "", nil, ""},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var rows []client.Lesson
			for i, updated := range tc.updated {
				rows = append(rows, lessonAt(string(rune('A'+i)), updated, false))
			}

			update, err := f.d.Checkout.FetchLessons(context.Background(), &fakeLessons{lessons: rows, etag: `"e"`}, "remote", State{LessonsSeenAt: tc.stored})

			if err != nil || update == nil || update.SeenAt != tc.want {
				t.Fatalf("update = %+v, err = %v", update, err)
			}
		})
	}
}

func TestFetchLessons_namesTheChangedTitlesInOneLine(t *testing.T) {
	f := newFixture(t)
	source := &fakeLessons{lessons: []client.Lesson{
		lessonAt("Old", "2026-10-01T10:00:00Z", false),
		lessonAt("New\nrule  here", "2026-10-02T10:00:00Z", true),
	}, etag: `"e"`}

	update, err := f.d.Checkout.FetchLessons(context.Background(), source, "remote", State{LessonsETag: `"e0"`, LessonsSeenAt: "2026-10-01T10:00:00Z"})

	if err != nil || len(update.Changed) != 1 || update.Changed[0] != "New rule here" || len(update.Lessons) != 2 {
		t.Fatalf("update = %+v, err = %v", update, err)
	}
}

func TestFetchLessons_firstContactNamesNothingButAnEmptyAnswerBeforeItNamesEverything(t *testing.T) {
	f := newFixture(t)
	rows := []client.Lesson{lessonAt("One", "2026-10-01T10:00:00Z", false), lessonAt("Two", "2026-10-02T10:00:00Z", false)}

	first, err := f.d.Checkout.FetchLessons(context.Background(), &fakeLessons{lessons: rows, etag: `"e"`}, "remote", State{})
	if err != nil || len(first.Changed) != 0 {
		t.Fatalf("first contact: %+v, %v", first, err)
	}

	afterEmpty, err := f.d.Checkout.FetchLessons(context.Background(), &fakeLessons{lessons: rows, etag: `"e"`}, "remote", State{LessonsETag: `"none"`})
	if err != nil || len(afterEmpty.Changed) != 2 {
		t.Fatalf("after an empty list: %+v, %v", afterEmpty, err)
	}
}

func TestFetchLessons_aFailureAndATimeKayaWouldRefuseAreErrors(t *testing.T) {
	f := newFixture(t)
	offline := errors.New("connection refused")

	if update, err := f.d.Checkout.FetchLessons(context.Background(), &fakeLessons{err: offline}, "remote", State{}); !errors.Is(err, offline) || update != nil {
		t.Fatalf("update = %+v, err = %v", update, err)
	}
	bad := &fakeLessons{lessons: []client.Lesson{lessonAt("One", "yesterday", false)}, etag: `"e"`}
	if update, err := f.d.Checkout.FetchLessons(context.Background(), bad, "remote", State{}); err == nil || update != nil {
		t.Fatalf("an updatedAt that is no instant was accepted: %+v", update)
	}
	if _, err := os.Stat(f.d.Checkout.lessonsPath()); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("a failed fetch wrote the file: %v", err)
	}
}

func TestStoreLessons_writesTheFileThenBothStateFieldsAndKeepsTheRestOfTheState(t *testing.T) {
	f := newFixture(t)
	checkout := f.d.Checkout
	if err := checkout.SaveState(State{Active: 4, Highest: 4, LessonsSeenAt: "2026-10-09T10:00:00Z"}); err != nil {
		t.Fatal(err)
	}

	err := checkout.StoreLessons(context.Background(), LessonsUpdate{
		Lessons: []client.Lesson{lessonAt("One", "", false), lessonAt("Two\nlines", "", false)},
		ETag:    `"e5"`,
		SeenAt:  "2026-10-02T10:00:00Z",
	})

	if err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(checkout.lessonsPath())
	if err != nil || string(data) != lessonsHeader+"\n\n- One\n- Two lines\n" {
		t.Fatalf("file = %q, err = %v", data, err)
	}
	info, err := os.Stat(checkout.lessonsPath())
	if err != nil || info.Mode().Perm() != 0o600 {
		t.Fatalf("mode = %v, err = %v", info, err)
	}
	state, err := checkout.LoadState()
	if err != nil || state.Active != 4 || state.Highest != 4 || state.LessonsETag != `"e5"` || state.LessonsSeenAt != "2026-10-09T10:00:00Z" {
		t.Fatalf("state = %+v, err = %v", state, err)
	}
}

func TestStoreLessons_aDamagedStateFailsAfterTheFileAndLeavesTheStateAlone(t *testing.T) {
	f := newFixture(t)
	checkout := f.d.Checkout
	if err := checkout.SaveState(State{}); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(checkout.statePath(), []byte("{broken"), 0o600); err != nil {
		t.Fatal(err)
	}

	err := checkout.StoreLessons(context.Background(), LessonsUpdate{ETag: `"e"`, SeenAt: "2026-10-02T10:00:00Z"})

	if err == nil || !strings.Contains(err.Error(), "damaged") {
		t.Fatalf("err = %v", err)
	}
	if data, _ := os.ReadFile(checkout.statePath()); string(data) != "{broken" {
		t.Fatalf("state = %q", data)
	}
}
