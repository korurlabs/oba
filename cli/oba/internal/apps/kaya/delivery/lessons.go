package delivery

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// lessonsHeader opens .kaya/lessons.md.
const lessonsHeader = "Derived from Kaya. Do not edit; run oba kaya lessons list for the text."

// LessonSource is the part of the Kaya API a session start reads the checkout's lessons from.
type LessonSource interface {
	LessonsSince(ctx context.Context, remoteURL, etag, since string) ([]client.Lesson, string, bool, error)
}

// LessonsUpdate is a new list of the checkout's lessons, not yet stored.
type LessonsUpdate struct {
	Lessons []client.Lesson
	ETag    string
	// SeenAt is the newest updatedAt known after this answer: a list that is empty or holds only
	// older lessons, because the newest was deleted, keeps the stored value.
	SeenAt string
	// Changed holds the titles of the lessons new or edited since the stored seen time.
	Changed []string
}

func (c *Checkout) lessonsPath() string { return filepath.Join(c.kayaDir(), "lessons.md") }

// FetchLessons asks src for the lessons of remote, conditionally on state. It answers nil when
// they did not change, and changes nothing on disk.
func (c *Checkout) FetchLessons(ctx context.Context, src LessonSource, remote string, state State) (*LessonsUpdate, error) {
	sentETag := state.LessonsETag
	// rule-10-no-op -- reason: an unreadable file is asked for again as a missing one, and rewriting it then reports what is wrong
	if _, err := os.Stat(c.lessonsPath()); err != nil {
		// A 304 would leave the checkout without a file until the next lesson changes.
		sentETag = ""
	}
	lessons, etag, notModified, err := src.LessonsSince(ctx, remote, sentETag, state.LessonsSeenAt)
	if err != nil {
		return nil, err
	}
	if notModified {
		return nil, nil
	}
	times := []string{state.LessonsSeenAt}
	for _, lesson := range lessons {
		times = append(times, lesson.UpdatedAt)
	}
	seenAt, err := newestInstant(times...)
	if err != nil {
		return nil, fmt.Errorf("read the lessons of %s: %w", remote, err)
	}
	// An ETag without a seen time means the last answer was an empty list. Kaya marks nothing
	// without a since time, yet everything in this list is new to the checkout.
	allNew := state.LessonsSeenAt == "" && state.LessonsETag != ""
	var changed []string
	for _, lesson := range lessons {
		if lesson.Changed || allNew {
			changed = append(changed, oneLine(lesson.Title))
		}
	}
	return &LessonsUpdate{Lessons: lessons, ETag: etag, SeenAt: seenAt, Changed: changed}, nil
}

// StoreLessons rewrites .kaya/lessons.md and remembers what it was written from, in one hold of
// the checkout lock: a session starting beside this one must not pair its ETag with this file,
// and the state written back must keep what a sync stored meanwhile.
func (c *Checkout) StoreLessons(ctx context.Context, update LessonsUpdate) error {
	unlock, err := c.Lock(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	if err := c.WriteLessons(update.Lessons); err != nil {
		return err
	}
	state, err := c.LoadState()
	if err != nil {
		return err
	}
	seenAt, err := newestInstant(state.LessonsSeenAt, update.SeenAt)
	if err != nil {
		return fmt.Errorf("%s: %w", c.statePath(), err)
	}
	state.LessonsETag, state.LessonsSeenAt = update.ETag, seenAt
	return c.SaveState(state)
}

// WriteLessons writes the titles of lessons to .kaya/lessons.md, one line each.
func (c *Checkout) WriteLessons(lessons []client.Lesson) error {
	if err := c.ensureKayaDir(); err != nil {
		return err
	}
	var text strings.Builder
	text.WriteString(lessonsHeader + "\n\n")
	for _, lesson := range lessons {
		text.WriteString("- " + oneLine(lesson.Title) + "\n")
	}
	return writeFileAtomic(c.lessonsPath(), []byte(text.String()), 0o600)
}

// oneLine folds a title to one line. The web app takes a line break in a title, and in the
// session's context such a line would read as a line of Kaya's own.
func oneLine(title string) string {
	return strings.Join(strings.Fields(title), " ")
}

// newestInstant is the latest of the given times, as it was written; empty values are skipped.
// The times are compared as instants because Kaya prints a varying number of fraction digits.
func newestInstant(values ...string) (string, error) {
	var (
		newest     string
		newestTime time.Time
	)
	for _, value := range values {
		if value == "" {
			continue
		}
		parsed, err := time.Parse(time.RFC3339, value)
		if err != nil {
			return "", fmt.Errorf("the time %q is not an instant: %w", value, err)
		}
		if newest == "" || parsed.After(newestTime) {
			newest, newestTime = value, parsed
		}
	}
	return newest, nil
}
