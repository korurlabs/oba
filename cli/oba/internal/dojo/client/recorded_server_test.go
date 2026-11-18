package client

import (
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

// recorded is one request a test server received.
type recorded struct {
	method, path, org, body string
}

// adminServer answers every request with status and body and records it.
func adminServer(t *testing.T, status int, body string) (*httptest.Server, *[]recorded) {
	t.Helper()
	var reqs []recorded
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("read body: %v", err)
		}
		reqs = append(reqs, recorded{r.Method, r.URL.EscapedPath(), r.Header.Get("X-Organization-ID"), string(data)})
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv, &reqs
}

func onlyRequest(t *testing.T, reqs []recorded, want recorded) {
	t.Helper()
	if len(reqs) != 1 || reqs[0] != want {
		t.Fatalf("requests = %+v, want [%+v]", reqs, want)
	}
}
