package kayatest

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

// weakPrefix marks a weak ETag.
const weakPrefix = "W/"

// StoredLesson is a lesson as LessonsKaya keeps it.
type StoredLesson struct {
	ID, Title, Body string
	Repository      *string
	UpdatedAt       string
	Session         string
}

// LessonsCall is one request to the lessons list or search. Page, Amount and ETag are the paging
// query of a list call, as sent.
type LessonsCall struct {
	Remote, Since, IfNoneMatch, Search string
	Page, Amount, ETag                 string
}

// LessonsKaya serves /delivery/lessons like Kaya: a list in pages with an ETag over every lesson
// that answers 304 on page 1 and 409 on a later page once the lessons changed, a search with the
// text, one lesson, create and delete. Everything else goes to the delivery fake.
type LessonsKaya struct {
	Delivery http.Handler
	Mu       sync.Mutex
	Lessons  []StoredLesson
	ETag     string
	version  int
	// failStatus and failCode make the conditional list answer an error.
	FailStatus int
	FailCode   string
	// WeakETags makes the list answer its ETag weak, W/ before the quoted tag, as Kaya does behind a
	// proxy that compresses the list. ETag keeps the strong form either way.
	WeakETags bool
	// OnPage, when set, runs before a list page is answered and outside Mu, so that it may call Set
	// to change the lessons between two pages or wait for the request to end.
	OnPage  func(r *http.Request, page int)
	calls   []LessonsCall
	posts   []map[string]any
	Deleted []string
}

// Set replaces the lessons and changes the ETag.
func (k *LessonsKaya) Set(lessons ...StoredLesson) {
	k.Mu.Lock()
	defer k.Mu.Unlock()
	k.Lessons = lessons
	k.version++
	k.ETag = fmt.Sprintf(`"v%d"`, k.version)
}

// Recorded returns the calls of the lessons list and search so far.
func (k *LessonsKaya) Recorded() []LessonsCall {
	k.Mu.Lock()
	defer k.Mu.Unlock()
	return append([]LessonsCall(nil), k.calls...)
}

// Created returns the bodies of the lessons created so far.
func (k *LessonsKaya) Created() []map[string]any {
	k.Mu.Lock()
	defer k.Mu.Unlock()
	return append([]map[string]any(nil), k.posts...)
}

func lessonRow(l StoredLesson, since string, withBody bool) map[string]any {
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

// ServeHTTP answers the lessons calls and passes every other to Delivery.
func (k *LessonsKaya) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/kaya/delivery/lessons" && !strings.HasPrefix(r.URL.Path, "/kaya/delivery/lessons/") {
		k.Delivery.ServeHTTP(w, r)
		return
	}
	if r.Method == http.MethodGet && r.URL.Path == "/kaya/delivery/lessons" {
		k.Mu.Lock()
		onPage := k.OnPage
		k.Mu.Unlock()
		if onPage != nil {
			page, _ := strconv.Atoi(r.URL.Query().Get("page"))
			onPage(r, max(page, 1))
		}
	}
	k.Mu.Lock()
	defer k.Mu.Unlock()
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
		k.calls = append(k.calls, LessonsCall{
			Remote: query.Get("remoteUrl"), Since: query.Get("since"), IfNoneMatch: r.Header.Get("If-None-Match"),
			Page: query.Get("page"), Amount: query.Get("amount"), ETag: query.Get("etag"),
		})
		if k.FailStatus != 0 {
			fail(k.FailStatus, k.FailCode)
			return
		}
		k.listPage(w, r, fail)
	case r.Method == http.MethodGet && r.URL.Path == "/kaya/delivery/lessons/search":
		search := query.Get("search")
		k.calls = append(k.calls, LessonsCall{Remote: query.Get("remoteUrl"), Search: search})
		rows := []map[string]any{}
		for _, l := range k.Lessons {
			if strings.Contains(strings.ToLower(l.Title+"\n"+l.Body), strings.ToLower(search)) {
				rows = append(rows, lessonRow(l, "", true))
			}
		}
		reply(http.StatusOK, rows)
	case r.Method == http.MethodPost && r.URL.Path == "/kaya/delivery/lessons":
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		k.posts = append(k.posts, body)
		lesson := StoredLesson{ID: fmt.Sprintf("new-%d", len(k.posts)), UpdatedAt: "2026-10-20T10:00:00Z"}
		lesson.Title, _ = body["title"].(string)
		lesson.Body, _ = body["body"].(string)
		if wide, _ := body["repoWide"].(bool); !wide {
			repository := "demo"
			lesson.Repository = &repository
		}
		k.Lessons = append(k.Lessons, lesson)
		reply(http.StatusCreated, map[string]any{"id": lesson.ID, "url": "https://kaya.test/lessons/" + lesson.ID})
	case r.Method == http.MethodGet:
		id := strings.TrimPrefix(r.URL.Path, "/kaya/delivery/lessons/")
		for _, l := range k.Lessons {
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
		for i, l := range k.Lessons {
			if l.ID == id {
				k.Lessons = append(k.Lessons[:i], k.Lessons[i+1:]...)
				k.Deleted = append(k.Deleted, id)
				reply(http.StatusOK, nil)
				return
			}
		}
		fail(http.StatusNotFound, "KAY_NF_001")
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

// listPage answers one page of the list as Kaya does: page from 1 and amount up to
// client.LessonsPageSize, both optional, and from page 2 on the ETag of page 1 as etag. Both
// If-None-Match and etag compare weakly, so a W/ before the tag does not matter.
func (k *LessonsKaya) listPage(w http.ResponseWriter, r *http.Request, fail func(int, string)) {
	query := r.URL.Query()
	page, amount := 1, client.LessonsPageSize
	var err error
	if value := query.Get("page"); value != "" {
		if page, err = strconv.Atoi(value); err != nil || page < 1 {
			fail(http.StatusUnprocessableEntity, "GEN_VAL_001")
			return
		}
	}
	if value := query.Get("amount"); value != "" {
		if amount, err = strconv.Atoi(value); err != nil || amount < 1 || amount > client.LessonsPageSize {
			fail(http.StatusUnprocessableEntity, "GEN_VAL_001")
			return
		}
	}
	if page == 1 {
		if match := r.Header.Get("If-None-Match"); match != "" && strings.TrimPrefix(match, weakPrefix) == k.ETag {
			w.WriteHeader(http.StatusNotModified)
			return
		}
	} else {
		sent := query.Get("etag")
		if sent == "" {
			fail(http.StatusUnprocessableEntity, "GEN_VAL_001")
			return
		}
		if strings.Trim(strings.TrimPrefix(sent, weakPrefix), `"`) != strings.Trim(k.ETag, `"`) {
			fail(http.StatusConflict, client.CodeLessonsChanged)
			return
		}
	}
	total := len(k.Lessons)
	pages := (total + amount - 1) / amount
	rows := []map[string]any{}
	for i := (page - 1) * amount; i < min(page*amount, total); i++ {
		rows = append(rows, lessonRow(k.Lessons[i], query.Get("since"), false))
	}
	etag := k.ETag
	if k.WeakETags {
		etag = weakPrefix + etag
	}
	w.Header().Set("ETag", etag)
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": true,
		"data":    rows,
		"pagination": map[string]any{
			"totalElements": total, "totalPages": pages, "currentPage": page, "pageSize": amount,
			"first": page == 1, "last": page >= pages, "empty": len(rows) == 0,
		},
	})
}

// SetupLessons is a delivered checkout whose API also serves lessons.
func SetupLessons(t *testing.T) (*LessonsKaya, string) {
	t.Helper()
	kaya, root := SetupDelivery(t)
	lk := &LessonsKaya{Delivery: kaya}
	server := httptest.NewServer(lk)
	t.Cleanup(server.Close)
	t.Setenv(config.EnvAPIURL, server.URL)
	return lk, root
}

// CheckoutOf is the checkout at root.
func CheckoutOf(t *testing.T, root string) *delivery.Checkout {
	t.Helper()
	checkout, err := delivery.FindCheckout(root)
	if err != nil {
		t.Fatal(err)
	}
	return checkout
}

// RemoteOf is the remote the checkout at root names to Kaya.
func RemoteOf(t *testing.T, root string) string {
	t.Helper()
	remote, _, err := CheckoutOf(t, root).Remotes()
	if err != nil {
		t.Fatal(err)
	}
	return remote
}
