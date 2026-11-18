package auth

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestStore_savesOnlyForTheUser(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), "kaya", "credentials.json"))
	if _, err := store.Load(); !errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("load before save = %v, want ErrNotSignedIn", err)
	}
	creds := Credentials{AccessToken: "a", RefreshToken: "r", Issuer: "https://issuer", OrgID: "org"}
	if err := store.Save(creds); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(store.Path())
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Errorf("mode = %v, want 0600", info.Mode().Perm())
	}
	dir, err := os.Stat(filepath.Dir(store.Path()))
	if err != nil {
		t.Fatal(err)
	}
	if dir.Mode().Perm() != 0o700 {
		t.Errorf("directory mode = %v, want 0700", dir.Mode().Perm())
	}
	loaded, err := store.Load()
	if err != nil || loaded.AccessToken != "a" || loaded.OrgID != "org" {
		t.Fatalf("loaded %+v, %v", loaded, err)
	}
	if err := store.Delete(); err != nil {
		t.Fatal(err)
	}
	if err := store.Delete(); err != nil {
		t.Errorf("deleting twice is no error: %v", err)
	}
}

func TestTokenSource_refreshesOnceAndStores(t *testing.T) {
	var refreshes atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/realms/sensey/protocol/openid-connect/token" {
			http.NotFound(w, r)
			return
		}
		if err := r.ParseForm(); err != nil || r.Form.Get("grant_type") != "refresh_token" || r.Form.Get("refresh_token") != "old-refresh" {
			http.Error(w, "bad form", http.StatusBadRequest)
			return
		}
		refreshes.Add(1)
		_ = json.NewEncoder(w).Encode(TokenResponse{AccessToken: "new-access", RefreshToken: "new-refresh", ExpiresIn: 300})
	}))
	defer server.Close()

	store := NewStore(filepath.Join(t.TempDir(), "credentials.json"))
	creds := Credentials{AccessToken: "old-access", RefreshToken: "old-refresh", ExpiresAt: time.Now().Add(10 * time.Second),
		Issuer: server.URL + "/realms/sensey", ClientID: "kaya-cli"}
	source := NewTokenSource(store, creds)

	var wg sync.WaitGroup
	for range 8 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			token, err := source.Token(context.Background())
			if err != nil || token != "new-access" {
				t.Errorf("token = %q, %v", token, err)
			}
		}()
	}
	wg.Wait()
	if refreshes.Load() != 1 {
		t.Errorf("refreshes = %d, want 1", refreshes.Load())
	}
	stored, err := store.Load()
	if err != nil || stored.RefreshToken != "new-refresh" || stored.AccessToken != "new-access" {
		t.Errorf("stored %+v, %v", stored, err)
	}
}

func TestTokenSource_validTokenNeedsNoCall(t *testing.T) {
	source := NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")),
		Credentials{AccessToken: "fresh", ExpiresAt: time.Now().Add(time.Hour), Issuer: "http://127.0.0.1:1"})
	token, err := source.Token(context.Background())
	if err != nil || token != "fresh" {
		t.Fatalf("token = %q, %v", token, err)
	}
}

func TestPoll_waitsForApprovalAndStopsOnRefusal(t *testing.T) {
	var polls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		switch polls.Add(1) {
		case 1:
			w.WriteHeader(http.StatusBadRequest)
			_, _ = w.Write([]byte(`{"error":"authorization_pending"}`))
		case 2:
			_ = json.NewEncoder(w).Encode(TokenResponse{AccessToken: "jwt", ExpiresIn: 300})
		default:
			w.WriteHeader(http.StatusBadRequest)
			_, _ = w.Write([]byte(`{"error":"access_denied"}`))
		}
	}))
	defer server.Close()
	flow := NewDeviceFlow(server.URL, "kaya-cli")
	flow.sleep = func(context.Context, time.Duration) error { return nil }
	device := DeviceAuthorization{DeviceCode: "d", ExpiresIn: 60, Interval: 1}

	token, err := flow.Poll(context.Background(), server.URL, device)
	if err != nil || token.AccessToken != "jwt" {
		t.Fatalf("token = %+v, %v", token, err)
	}
	if _, err := flow.Poll(context.Background(), server.URL, device); err == nil {
		t.Fatal("a refusal in the browser ends the sign-in")
	}
}
