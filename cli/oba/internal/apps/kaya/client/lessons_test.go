package client

import (
	"context"
	"net/http"
	"net/http/httptest"
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
