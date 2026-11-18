package client

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

const ingestToken = "kyt_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

func ingestTokenFunc(context.Context) (string, error) { return ingestToken, nil }

func TestPostIngestBatch_sendsTheBodyWithTheMachineTokenAndReturnsTheData(t *testing.T) {
	var gotMethod, gotPath, gotAuth, gotType, gotAccept, gotBody string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod, gotPath = r.Method, r.URL.Path
		gotAuth, gotType, gotAccept = r.Header.Get("Authorization"), r.Header.Get("Content-Type"), r.Header.Get("Accept")
		body, _ := io.ReadAll(r.Body)
		gotBody = string(body)
		_, _ = w.Write([]byte(`{"success":true,"message":"ok","data":{"acknowledgedSeq":7}}`))
	}))
	defer server.Close()
	data, err := NewIngest(server.URL+"/", ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{"sessionId":"s"}`))
	if err != nil {
		t.Fatal(err)
	}
	if gotMethod != http.MethodPost || gotPath != "/kaya/ingest/batches" {
		t.Errorf("request = %s %s, want POST /ingest/batches", gotMethod, gotPath)
	}
	if gotAuth != "Bearer "+ingestToken || gotType != "application/json" || gotAccept != "application/json" {
		t.Errorf("headers = %q %q %q", gotAuth, gotType, gotAccept)
	}
	if gotBody != `{"sessionId":"s"}` {
		t.Errorf("body = %q, want it sent as given", gotBody)
	}
	if string(data) != `{"acknowledgedSeq":7}` {
		t.Errorf("data = %s", data)
	}
}

func TestPostIngestBatch_anAcceptedAnswerWithoutDataIsNilData(t *testing.T) {
	for name, body := range map[string]string{"empty": ``, "not json": `stored`, "null data": `{"success":true,"data":null}`, "no data": `{"success":true}`} {
		t.Run(name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write([]byte(body)) }))
			defer server.Close()
			data, err := NewIngest(server.URL, ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{}`))
			if err != nil || data != nil {
				t.Errorf("data, err = %s, %v; want nil, nil", data, err)
			}
		})
	}
}

func TestPostIngestBatch_refusalsCarryStatusCodeAndValues(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		_, _ = w.Write([]byte(`{"success":false,"message":"no","data":null,"error":{"code":"KAY_SEC_007","message":"unknown git email",` +
			`"errorValues":{"gitAuthorEmail":"dev@example.com"}}}`))
	}))
	defer server.Close()
	_, err := NewIngest(server.URL, ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{}`))
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != 403 || apiErr.Code != "KAY_SEC_007" || apiErr.Message != "unknown git email" {
		t.Fatalf("err = %#v", err)
	}
	if apiErr.ErrorValues["gitAuthorEmail"] != "dev@example.com" {
		t.Errorf("errorValues = %v", apiErr.ErrorValues)
	}
}

func TestPostIngestBatch_everyNon200IsARefusalEvenWithAnEnvelopeOfSuccess(t *testing.T) {
	for _, status := range []int{201, 204, 400, 401, 409, 413, 422, 429, 500, 503} {
		t.Run(http.StatusText(status), func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				w.WriteHeader(status)
				_, _ = w.Write([]byte(`{"success":true,"data":{"acknowledgedSeq":9}}`))
			}))
			defer server.Close()
			data, err := NewIngest(server.URL, ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{}`))
			var apiErr *APIError
			if !errors.As(err, &apiErr) || apiErr.Status != status || data != nil {
				t.Errorf("data, err = %s, %v; want an APIError with %d", data, err, status)
			}
		})
	}
}

func TestPostIngestBatch_aBodyWithoutEnvelopeKeepsTheText(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		_, _ = w.Write([]byte("  upstream down \n"))
	}))
	defer server.Close()
	_, err := NewIngest(server.URL, ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{}`))
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != 502 || apiErr.Code != "" || apiErr.Message != "upstream down" {
		t.Fatalf("err = %#v", err)
	}
}

func TestPostIngestBatch_followsNoRedirectAndSendsTheTokenNowhereElse(t *testing.T) {
	targetHits := 0
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		targetHits++
		_, _ = w.Write([]byte(`{"success":true,"data":{}}`))
	}))
	defer target.Close()
	for _, status := range []int{301, 302, 303, 307, 308} {
		redirecting := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Redirect(w, r, target.URL+r.URL.Path, status)
		}))
		_, err := NewIngest(redirecting.URL, ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{}`))
		redirecting.Close()
		var apiErr *APIError
		if !errors.As(err, &apiErr) || apiErr.Status != status {
			t.Errorf("status %d: err = %#v, want the redirect itself as an APIError", status, err)
		}
	}
	if targetHits != 0 {
		t.Errorf("the redirect target received %d requests, want none", targetHits)
	}
}

func TestPostIngestBatch_unreachableKayaIsNotAnAPIError(t *testing.T) {
	server := httptest.NewServer(http.NotFoundHandler())
	url := server.URL
	server.Close()
	_, err := NewIngest(url, ingestTokenFunc).PostIngestBatch(context.Background(), []byte(`{}`))
	var apiErr *APIError
	if err == nil || errors.As(err, &apiErr) {
		t.Fatalf("err = %#v, want a transport error", err)
	}
	if strings.Contains(err.Error(), ingestToken) {
		t.Error("the error names the token")
	}
}

func TestPostIngestBatch_aFailingTokenSendsNothing(t *testing.T) {
	hits := 0
	server := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { hits++ }))
	defer server.Close()
	boom := errors.New("no token")
	_, err := NewIngest(server.URL, func(context.Context) (string, error) { return "", boom }).PostIngestBatch(context.Background(), []byte(`{}`))
	if !errors.Is(err, boom) || hits != 0 {
		t.Errorf("err = %v, hits = %d; want the token error and no request", err, hits)
	}
}

func TestPostIngestBatch_aCancelledContextReturnsItsError(t *testing.T) {
	started := make(chan struct{})
	server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		_, _ = io.Copy(io.Discard, r.Body)
		close(started)
		select {
		case <-r.Context().Done():
		case <-time.After(10 * time.Second):
		}
	}))
	defer server.Close()
	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		<-started
		cancel()
	}()
	_, err := NewIngest(server.URL, ingestTokenFunc).PostIngestBatch(ctx, []byte(`{}`))
	if !errors.Is(err, context.Canceled) {
		t.Errorf("err = %v, want context.Canceled", err)
	}
}

func TestNewIngest_boundsOneCallToFifteenSeconds(t *testing.T) {
	if IngestTimeout != 15*time.Second {
		t.Errorf("IngestTimeout = %v, want 15 s", IngestTimeout)
	}
	if got := NewIngest("http://127.0.0.1", ingestTokenFunc).http.Timeout; got != IngestTimeout {
		t.Errorf("http timeout = %v, want %v", got, IngestTimeout)
	}
}
