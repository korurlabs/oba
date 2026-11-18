package auth

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestTokenEndpoint(t *testing.T) {
	if got := TokenEndpoint("https://x/realms/sensey/"); got != "https://x/realms/sensey/protocol/openid-connect/token" {
		t.Fatalf("TokenEndpoint = %s", got)
	}
}

func TestDiscoverAndAuthorize(t *testing.T) {
	mux := http.NewServeMux()
	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)
	mux.HandleFunc("/.well-known/openid-configuration", func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(Endpoints{DeviceAuthorizationEndpoint: srv.URL + "/device", TokenEndpoint: srv.URL + "/token"})
	})
	mux.HandleFunc("/device", func(w http.ResponseWriter, r *http.Request) {
		_ = r.ParseForm()
		if r.PostForm.Get("client_id") != "kalkan-cli" || r.PostForm.Get("scope") != "openid" {
			t.Errorf("device form = %v", r.PostForm)
		}
		_ = json.NewEncoder(w).Encode(DeviceAuthorization{DeviceCode: "dc", UserCode: "UC", VerificationURI: "v", Interval: 1, ExpiresIn: 60})
	})
	flow := NewDeviceFlow(srv.URL+"/", "kalkan-cli")
	endpoints, err := flow.Discover(context.Background())
	if err != nil || endpoints.TokenEndpoint != srv.URL+"/token" {
		t.Fatalf("Discover() = %+v, %v", endpoints, err)
	}
	authz, err := flow.Authorize(context.Background(), endpoints.DeviceAuthorizationEndpoint)
	if err != nil || authz.DeviceCode != "dc" || authz.UserCode != "UC" {
		t.Fatalf("Authorize() = %+v, %v", authz, err)
	}
}

func TestDiscoverFailures(t *testing.T) {
	cases := map[string]struct {
		handler http.HandlerFunc
		want    string
	}{
		"status": {func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(500) }, "answered 500"},
		"json":   {func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write([]byte("nope")) }, "parse"},
		"no device endpoint": {func(w http.ResponseWriter, _ *http.Request) {
			_, _ = w.Write([]byte(`{"token_endpoint":"x"}`))
		}, "offers no device authorization"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			srv := httptest.NewServer(tc.handler)
			defer srv.Close()
			_, err := NewDeviceFlow(srv.URL, "c").Discover(context.Background())
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("Discover() = %v, want %q", err, tc.want)
			}
		})
	}
}

func TestAuthorizeRefusal(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(400)
		_, _ = w.Write([]byte("unauthorized_client"))
	}))
	defer srv.Close()
	_, err := NewDeviceFlow(srv.URL, "c").Authorize(context.Background(), srv.URL)
	if err == nil || !strings.Contains(err.Error(), "unauthorized_client") {
		t.Fatalf("Authorize() = %v", err)
	}
}

func pollFlow(t *testing.T, answers []func(w http.ResponseWriter)) (*DeviceFlow, *httptest.Server, *[]time.Duration) {
	t.Helper()
	var n atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = r.ParseForm()
		if r.PostForm.Get("grant_type") != "urn:ietf:params:oauth:grant-type:device_code" || r.PostForm.Get("device_code") != "dc" {
			t.Errorf("poll form = %v", r.PostForm)
		}
		i := int(n.Add(1)) - 1
		if i >= len(answers) {
			i = len(answers) - 1
		}
		answers[i](w)
	}))
	t.Cleanup(srv.Close)
	flow := NewDeviceFlow("http://unused", "c")
	var waits []time.Duration
	flow.sleep = func(_ context.Context, d time.Duration) error { waits = append(waits, d); return nil }
	return flow, srv, &waits
}

func jsonAnswer(status int, body string) func(w http.ResponseWriter) {
	return func(w http.ResponseWriter) {
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}
}

func TestPollWaitsThroughPendingAndSlowDown(t *testing.T) {
	flow, srv, waits := pollFlow(t, []func(http.ResponseWriter){
		jsonAnswer(400, `{"error":"authorization_pending"}`),
		jsonAnswer(400, `{"error":"slow_down"}`),
		jsonAnswer(200, `{"access_token":"tok","refresh_token":"r","expires_in":60}`),
	})
	token, err := flow.Poll(context.Background(), srv.URL, DeviceAuthorization{DeviceCode: "dc", Interval: 2, ExpiresIn: 60})
	if err != nil || token.AccessToken != "tok" {
		t.Fatalf("Poll() = %+v, %v", token, err)
	}
	want := []time.Duration{2 * time.Second, 2 * time.Second, 7 * time.Second}
	if len(*waits) != 3 || (*waits)[0] != want[0] || (*waits)[1] != want[1] || (*waits)[2] != want[2] {
		t.Fatalf("waits = %v, want %v", *waits, want)
	}
}

func TestPollDefaultInterval(t *testing.T) {
	flow, srv, waits := pollFlow(t, []func(http.ResponseWriter){jsonAnswer(200, `{"access_token":"tok"}`)})
	if _, err := flow.Poll(context.Background(), srv.URL, DeviceAuthorization{DeviceCode: "dc", ExpiresIn: 60}); err != nil {
		t.Fatal(err)
	}
	if (*waits)[0] != 5*time.Second {
		t.Fatalf("interval = %v, want 5s", (*waits)[0])
	}
}

func TestPollRefusals(t *testing.T) {
	cases := map[string]struct{ body, want string }{
		"expired":  {`{"error":"expired_token"}`, "expired"},
		"denied":   {`{"error":"access_denied"}`, "refused in the browser"},
		"other":    {`{"error":"boom","error_description":"why"}`, "boom: why"},
		"not json": {`<html>`, "answered 400"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			flow, srv, _ := pollFlow(t, []func(http.ResponseWriter){jsonAnswer(400, tc.body)})
			_, err := flow.Poll(context.Background(), srv.URL, DeviceAuthorization{DeviceCode: "dc", Interval: 1, ExpiresIn: 60})
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("Poll() = %v, want %q", err, tc.want)
			}
		})
	}
}

func TestPollExpiredLocally(t *testing.T) {
	flow, srv, _ := pollFlow(t, []func(http.ResponseWriter){jsonAnswer(200, `{"access_token":"x"}`)})
	_, err := flow.Poll(context.Background(), srv.URL, DeviceAuthorization{DeviceCode: "dc", Interval: 1, ExpiresIn: -1})
	if err == nil || !strings.Contains(err.Error(), "expired") {
		t.Fatalf("Poll() = %v", err)
	}
}

func TestPollStopsOnCancelledContext(t *testing.T) {
	flow := NewDeviceFlow("http://unused", "c")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := flow.Poll(ctx, "http://127.0.0.1:1", DeviceAuthorization{Interval: 1, ExpiresIn: 60})
	if err != context.Canceled {
		t.Fatalf("Poll() = %v, want context.Canceled", err)
	}
}

func TestParseTokenWithoutAccessToken(t *testing.T) {
	if _, err := parseToken([]byte(`{}`)); err == nil {
		t.Fatal("want an error for a token response with no access token")
	}
	if _, err := parseToken([]byte(`x`)); err == nil {
		t.Fatal("want a parse error")
	}
}
