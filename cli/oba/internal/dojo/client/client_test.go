package client

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestParseError(t *testing.T) {
	tests := []struct {
		name   string
		status int
		body   string
		want   string
	}{
		{"envelope with fields", 400,
			`{"error":{"code":"OTK_REL_001","message":"bad","errorValues":{"name":"blank","a":"x","n":3}}}`,
			"OTK_REL_001: bad\n  a: x\n  n: 3\n  name: blank"},
		{"envelope message only", 400, `{"message":"plain"}`, "plain"},
		{"empty 403", 403, ``, "access denied. You may not have permission for this operation"},
		{"empty 404", 404, `{}`, "resource not found"},
		{"empty 401", 401, ``, "unauthorized. Run 'oba login'"},
		{"html 500", 500, `<html>oops</html>`, "request failed (500): <html>oops</html>"},
		{"empty 502", 502, ``, "request failed (502): Bad Gateway"},
		{"long body", 500, strings.Repeat("x", 300), "request failed (500): " + strings.Repeat("x", 200) + "..."},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := ParseError(tt.status, []byte(tt.body))
			if err.Status != tt.status || err.Error() != tt.want {
				t.Errorf("got %d %q, want %d %q", err.Status, err.Error(), tt.status, tt.want)
			}
		})
	}
}

func TestParseErrorFields(t *testing.T) {
	err := ParseError(400, []byte(`{"error":{"code":"C","message":"m","errorValues":{"k":"v"}}}`))
	if err.Code != "C" || err.Message != "m" || err.Fields["k"] != "v" {
		t.Errorf("%+v", err)
	}
}

func TestClientHeadersAndRetries(t *testing.T) {
	var calls int
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.Header.Get("Authorization") != "Bearer tok" {
			t.Errorf("auth = %q", r.Header.Get("Authorization"))
		}
		if r.Header.Get("X-Organization-ID") != "org" {
			t.Errorf("org = %q", r.Header.Get("X-Organization-ID"))
		}
		if calls == 1 {
			w.Header().Set("Retry-After", "0")
			w.WriteHeader(http.StatusTooManyRequests)
			return
		}
		_, _ = w.Write([]byte(`{"data":1}`))
	}))
	defer srv.Close()
	c := NewWithToken(srv.URL+"/", "org", "tok")
	body, err := c.Get("/x")
	if err != nil || string(body) != `{"data":1}` || calls != 2 {
		t.Errorf("body %q err %v calls %d", body, err, calls)
	}
}

func TestClientRefreshesOnce(t *testing.T) {
	var calls int
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.Header.Get("Authorization") == "Bearer old" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		if r.Header.Get("X-Organization-ID") != "" {
			t.Errorf("org header sent for empty org")
		}
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()
	refreshed := 0
	c := New(srv.URL, "", func() (string, error) { return "old", nil }, func() (string, error) { refreshed++; return "new", nil })
	if _, err := c.Get("/x"); err != nil || refreshed != 1 || calls != 2 {
		t.Errorf("err %v refreshed %d calls %d", err, refreshed, calls)
	}
}

func TestClientMarksAFailedRefreshAsAnExpiredSession(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()
	refreshErr := errors.New("invalid_grant")
	c := New(srv.URL, "", func() (string, error) { return "old", nil }, func() (string, error) { return "", refreshErr })
	_, err := c.Get("/x")
	if !errors.Is(err, ErrSessionExpired) || !errors.Is(err, refreshErr) {
		t.Fatalf("err = %v, want ErrSessionExpired wrapping the refresh error", err)
	}
	if want := "your session has expired and could not be refreshed (invalid_grant). Run 'oba login'"; err.Error() != want {
		t.Errorf("err = %q, want %q", err, want)
	}
}
