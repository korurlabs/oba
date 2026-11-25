package client

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
)

const (
	// lessonsPath is the machine-token resource of lessons.
	lessonsPath = "/kaya/delivery/lessons"
	// LessonsPageSize is the most lessons one page of the list holds; Kaya refuses more.
	LessonsPageSize = 100
	// CodeLessonsChanged (409): the lessons changed after page 1 of a read, so the etag a later
	// page sends no longer names them.
	CodeLessonsChanged = "KAY_VAL_017"
	// lessonsRestarts bounds how often one read starts again at page 1 because the lessons changed
	// between its pages; lessons edited faster than that are read by the next call.
	lessonsRestarts = 2
	// lessonsMaxPages bounds the pages one read requests, whatever Kaya answers: 100 pages of
	// LessonsPageSize hold 10,000 lessons, far more than an organization keeps, so a Kaya that
	// reports more is broken or hostile. Without the bound such a Kaya could keep a read going,
	// and growing in memory, until the caller gives up. A read past it fails rather than answer
	// part of the list as if it were all.
	lessonsMaxPages = 100
	// maxLessonPageBytes bounds what the client reads of one page of the list, in place of the
	// general maxResponseBytes. A listed lesson carries its id, a title of at most 300 characters,
	// a repository name of at most 100, changed and updatedAt; even with every character escaped
	// as \uXXXX it stays under 2,600 bytes, so a full page of LessonsPageSize stays under 260 KB.
	// 1 MiB leaves about four times that. A larger bound would let a broken or hostile Kaya send
	// millions of empty lessons per page, which decode to many times their size in memory.
	maxLessonPageBytes = 1 << 20
	// weakETagPrefix marks a weak ETag. A proxy that compresses the list, as a CDN in front of
	// Kaya does, turns its strong ETag into a weak one with the same tag. The etag of a later page
	// names the lessons by the tag alone, so it goes without the prefix, which a Kaya that accepts
	// only the strong form also takes. If-None-Match keeps it: HTTP compares that header weakly
	// (RFC 9110 section 13.1.2), so a stored weak ETag still answers 304.
	weakETagPrefix = "W/"
)

// Lesson is one row of the lessons of a checkout: its repository's plus the organization-wide
// ones. Repository is nil for an organization-wide lesson. Only a search carries Body, and only
// a list with a since time sets Changed.
type Lesson struct {
	ID         string  `json:"id"`
	Title      string  `json:"title"`
	Body       string  `json:"body,omitempty"`
	Repository *string `json:"repository"`
	Changed    bool    `json:"changed"`
	UpdatedAt  string  `json:"updatedAt"`
}

// LessonReference names a repository or a user of a lesson.
type LessonReference struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// LessonSession is the session that recorded a lesson, once Kaya knows it.
type LessonSession struct {
	ID        string `json:"id"`
	SessionID string `json:"sessionId"`
	Title     string `json:"title"`
}

// LessonDetail is a whole lesson. Repository is nil for an organization-wide lesson; Session is
// nil when no session recorded it or Kaya does not know that session yet.
type LessonDetail struct {
	ID              string           `json:"id"`
	Title           string           `json:"title"`
	Body            string           `json:"body"`
	Repository      *LessonReference `json:"repository"`
	User            LessonReference  `json:"user"`
	ClaudeSessionID string           `json:"claudeSessionId"`
	CreatedAt       string           `json:"createdAt"`
	UpdatedAt       string           `json:"updatedAt"`
	CanEdit         bool             `json:"canEdit"`
	Session         *LessonSession   `json:"session"`
}

// LessonRequest is the body of POST /kaya/delivery/lessons. RemoteURL is required even with RepoWide,
// which stores the lesson without a repository. An empty ClaudeSessionID stays off the wire: the
// server checks a sent id against the session id pattern.
type LessonRequest struct {
	RemoteURL       string `json:"remoteUrl"`
	Title           string `json:"title"`
	Body            string `json:"body"`
	RepoWide        bool   `json:"repoWide"`
	ClaudeSessionID string `json:"claudeSessionId,omitempty"`
}

// LessonCreated is the answer of a new lesson; URL opens it in the web app.
type LessonCreated struct {
	ID  string `json:"id"`
	URL string `json:"url"`
}

// Lessons lists every lesson of the checkout whose remote is remoteURL, newest first, without
// their text. A remote Kaya does not know gives the organization-wide ones.
func (c *Client) Lessons(ctx context.Context, remoteURL string) ([]Lesson, error) {
	lessons, _, _, err := c.LessonsSince(ctx, remoteURL, "", "")
	return lessons, err
}

// LessonsSince is Lessons for a session start. With an etag from an earlier answer, a 304 answers
// notModified and no list; with a since time, the lessons Kaya changed after it are marked Changed.
// It answers the whole list or an error, never a part: when the lessons change between two pages
// it reads them again from page 1, at most lessonsRestarts times, and then answers Kaya's refusal.
func (c *Client) LessonsSince(ctx context.Context, remoteURL, etag, since string) (lessons []Lesson, newETag string, notModified bool, err error) {
	for restarts := 0; ; restarts++ {
		lessons, newETag, notModified, err = c.lessonPages(ctx, remoteURL, etag, since)
		if restarts == lessonsRestarts || !IsCode(err, CodeLessonsChanged) {
			return lessons, newETag, notModified, err
		}
	}
}

// lessonPages reads the list page by page until Kaya marks the last one. Page 1 carries etag as
// If-None-Match, so a 304 skips every page; each later page carries the ETag of page 1, without
// weakETagPrefix, so Kaya refuses it with CodeLessonsChanged instead of mixing two states of the
// lessons. The ETag it answers is page 1's as received, weak or strong. An answer without paging
// details, from a Kaya that does not page, is the whole list. The page count of page 1 bounds the
// read: a later page may end it sooner, never later, and a count above lessonsMaxPages fails the
// read before page 2. A page larger than maxLessonPageBytes, or with more than LessonsPageSize
// lessons, fails the read too.
func (c *Client) lessonPages(ctx context.Context, remoteURL, etag, since string) ([]Lesson, string, bool, error) {
	var (
		all      []Lesson
		firstTag string
		lastPage int
	)
	for page := 1; ; page++ {
		query := url.Values{"remoteUrl": {remoteURL}, "page": {strconv.Itoa(page)}, "amount": {strconv.Itoa(LessonsPageSize)}}
		if since != "" {
			query.Set("since", since)
		}
		header := http.Header{}
		if page == 1 && etag != "" {
			header.Set("If-None-Match", etag)
		}
		if page > 1 && firstTag != "" {
			query.Set("etag", strings.TrimPrefix(firstTag, weakETagPrefix))
		}
		resp, raw, err := c.raw(ctx, lessonsPath+"?"+query.Encode(), header, maxLessonPageBytes+1)
		if err != nil {
			return nil, "", false, fmt.Errorf("read page %d of the lessons: %w", page, err)
		}
		if page == 1 && resp.StatusCode == http.StatusNotModified {
			return nil, etag, true, nil
		}
		if len(raw) > maxLessonPageBytes {
			return nil, "", false, fmt.Errorf("read page %d of the lessons: the answer is larger than the %d bytes a page takes", page, maxLessonPageBytes)
		}
		var lessons []Lesson
		pagination, err := decodePage(resp, raw, lessonsPath, &lessons)
		if err != nil {
			return nil, "", false, fmt.Errorf("read page %d of the lessons: %w", page, err)
		}
		if len(lessons) > LessonsPageSize {
			return nil, "", false, fmt.Errorf("read page %d of the lessons: Kaya sent %d lessons, more than the %d a page holds", page, len(lessons), LessonsPageSize)
		}
		if page == 1 {
			firstTag = resp.Header.Get("ETag")
			if pagination != nil {
				if pagination.TotalPages > lessonsMaxPages {
					return nil, "", false, fmt.Errorf("read the lessons: Kaya reports %d pages of them, more than the %d one read takes", pagination.TotalPages, lessonsMaxPages)
				}
				lastPage = pagination.TotalPages
			}
		}
		all = append(all, lessons...)
		if pagination == nil || pagination.Last || page >= lastPage || page >= pagination.TotalPages || len(lessons) == 0 {
			return all, firstTag, false, nil
		}
	}
}

// SearchLessons finds the lessons of the checkout whose title or text holds search (at most 200
// characters, case ignored), newest first, at most 100, with their text.
func (c *Client) SearchLessons(ctx context.Context, remoteURL, search string) ([]Lesson, error) {
	query := url.Values{"remoteUrl": {remoteURL}, "search": {search}}
	var lessons []Lesson
	return lessons, c.doJSON(ctx, http.MethodGet, lessonsPath+"/search?"+query.Encode(), nil, &lessons)
}

// LessonByID reads a whole lesson of the token's organization.
func (c *Client) LessonByID(ctx context.Context, lessonID string) (LessonDetail, error) {
	var lesson LessonDetail
	return lesson, c.doJSON(ctx, http.MethodGet, lessonsPath+"/"+url.PathEscape(lessonID), nil, &lesson)
}

// AddLesson records a lesson; the token's user is its author.
func (c *Client) AddLesson(ctx context.Context, request LessonRequest) (LessonCreated, error) {
	var created LessonCreated
	return created, c.doJSON(ctx, http.MethodPost, lessonsPath, request, &created)
}

// RemoveLesson deletes a lesson for good; only its author, an ADMIN or an OWNER may.
func (c *Client) RemoveLesson(ctx context.Context, lessonID string) error {
	return c.doJSON(ctx, http.MethodDelete, lessonsPath+"/"+url.PathEscape(lessonID), nil, nil)
}
