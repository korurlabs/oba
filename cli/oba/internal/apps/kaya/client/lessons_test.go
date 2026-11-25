package client

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
)

func TestLessons_anAnswerWithoutPagingIsTheWholeList(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		if query := r.URL.Query(); query.Get("page") != "1" || query.Get("amount") != "100" || query.Get("etag") != "" {
			t.Errorf("query = %v", query)
		}
		w.Header().Set("ETag", `"one"`)
		_, _ = w.Write([]byte(`{"success":true,"data":[{"id":"l1","title":"One"},{"id":"l2","title":"Two"}]}`))
	}))
	defer server.Close()
	api := New(server.URL, "", func(context.Context) (string, error) { return "kyt_test", nil })

	lessons, err := api.Lessons(context.Background(), "github.com/acme/demo")

	if err != nil || len(lessons) != 2 || lessons[1].ID != "l2" || calls.Load() != 1 {
		t.Fatalf("lessons = %+v, calls = %d, err = %v", lessons, calls.Load(), err)
	}
}

// pagingServer answers every page with one lesson, never marked last, and the page count that
// totalPages gives for that page. After stopAfter calls it marks the page last, so a walk that
// ignores its own bound ends with too many lessons instead of running until the test times out.
func pagingServer(t *testing.T, totalPages func(page int) int, stopAfter int32) (*httptest.Server, *atomic.Int32) {
	t.Helper()
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		call := calls.Add(1)
		page, err := strconv.Atoi(r.URL.Query().Get("page"))
		if err != nil {
			t.Errorf("page = %q", r.URL.Query().Get("page"))
			return
		}
		if page > 1 && r.URL.Query().Get("etag") != `"one"` {
			t.Errorf("page %d etag = %q", page, r.URL.Query().Get("etag"))
		}
		w.Header().Set("ETag", `"one"`)
		_, _ = fmt.Fprintf(w, `{"success":true,"data":[{"id":"l%d","title":"Lesson %d"}],"pagination":{"totalPages":%d,"last":%t}}`,
			page, page, totalPages(page), call >= stopAfter)
	}))
	t.Cleanup(server.Close)
	return server, &calls
}

func TestLessons_laterPagesCannotRaiseThePageCount(t *testing.T) {
	server, calls := pagingServer(t, func(page int) int {
		if page == 1 {
			return 3
		}
		return page + 5
	}, 50)
	api := New(server.URL, "", func(context.Context) (string, error) { return "kyt_test", nil })

	lessons, err := api.Lessons(context.Background(), "github.com/acme/demo")

	if err != nil || len(lessons) != 3 || lessons[2].ID != "l3" || calls.Load() != 3 {
		t.Fatalf("lessons = %d, calls = %d, err = %v; want the 3 pages page 1 reported", len(lessons), calls.Load(), err)
	}
}

func TestLessons_morePagesThanTheMostIsAnError(t *testing.T) {
	server, calls := pagingServer(t, func(int) int { return lessonsMaxPages + 1 }, lessonsMaxPages+1)
	api := New(server.URL, "", func(context.Context) (string, error) { return "kyt_test", nil })

	lessons, err := api.Lessons(context.Background(), "github.com/acme/demo")

	if err == nil || lessons != nil || calls.Load() != 1 {
		t.Fatalf("lessons = %d, calls = %d, err = %v; want an error after page 1", len(lessons), calls.Load(), err)
	}
	if want := fmt.Sprintf("Kaya reports %d pages", lessonsMaxPages+1); !strings.Contains(err.Error(), want) {
		t.Fatalf("err = %v, want it to contain %q", err, want)
	}
}

func TestLessons_readsTheMostPages(t *testing.T) {
	server, calls := pagingServer(t, func(int) int { return lessonsMaxPages }, lessonsMaxPages)
	api := New(server.URL, "", func(context.Context) (string, error) { return "kyt_test", nil })

	lessons, err := api.Lessons(context.Background(), "github.com/acme/demo")

	if err != nil || len(lessons) != lessonsMaxPages || calls.Load() != lessonsMaxPages {
		t.Fatalf("lessons = %d, calls = %d, err = %v", len(lessons), calls.Load(), err)
	}
}
