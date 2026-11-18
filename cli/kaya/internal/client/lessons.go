package client

import (
	"context"
	"net/http"
	"net/url"
)

// lessonsPath is the machine-token resource of lessons.
const lessonsPath = "/kaya/delivery/lessons"

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

// Lessons lists the lessons of the checkout whose remote is remoteURL, newest first, at most
// 100, without their text. A remote Kaya does not know gives the organization-wide ones.
func (c *Client) Lessons(ctx context.Context, remoteURL string) ([]Lesson, error) {
	var lessons []Lesson
	return lessons, c.doJSON(ctx, http.MethodGet, lessonsPath+"?"+url.Values{"remoteUrl": {remoteURL}}.Encode(), nil, &lessons)
}

// LessonsSince is Lessons for a session start. With an etag from an earlier answer, a 304 answers
// notModified and no list; with a since time, the lessons Kaya changed after it are marked Changed.
func (c *Client) LessonsSince(ctx context.Context, remoteURL, etag, since string) (lessons []Lesson, newETag string, notModified bool, err error) {
	query := url.Values{"remoteUrl": {remoteURL}}
	if since != "" {
		query.Set("since", since)
	}
	header := http.Header{}
	if etag != "" {
		header.Set("If-None-Match", etag)
	}
	resp, raw, err := c.raw(ctx, lessonsPath+"?"+query.Encode(), header, maxResponseBytes)
	if err != nil {
		return nil, "", false, err
	}
	if resp.StatusCode == http.StatusNotModified {
		return nil, etag, true, nil
	}
	if err := decodeData(resp, raw, lessonsPath, &lessons); err != nil {
		return nil, "", false, err
	}
	return lessons, resp.Header.Get("ETag"), false, nil
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
