package auth

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestStoreRoundTripAndMode(t *testing.T) {
	store := NewStore(filepath.Join(t.TempDir(), "sub", "credentials.json"))
	if _, err := store.Load(); !errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("Load() on no file = %v, want ErrNotSignedIn", err)
	}
	want := Credentials{AccessToken: "a", RefreshToken: "r", Issuer: "i", ClientID: "c", APIURL: "u", OrgID: "o",
		Memberships: []Membership{{OrganizationID: "o", Name: "Org", Role: "ADMIN"}}}
	if err := store.Save(want); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(store.Path())
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("mode = %v, want 0600", info.Mode().Perm())
	}
	got, err := store.Load()
	if err != nil || got.AccessToken != "a" || got.OrgID != "o" || len(got.Memberships) != 1 {
		t.Fatalf("Load() = %+v, %v", got, err)
	}
	if err := store.Delete(); err != nil {
		t.Fatal(err)
	}
	if err := store.Delete(); err != nil {
		t.Fatalf("Delete() of no file = %v, want nil", err)
	}
	if _, err := store.Load(); !errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("Load() after Delete = %v", err)
	}
}

func TestStoreLoadRejectsCorruptFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "credentials.json")
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := NewStore(path).Load()
	if err == nil || errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("Load() = %v, want a parse error that is not ErrNotSignedIn", err)
	}
}

// tokenServer answers the Keycloak token endpoint and counts the calls.
func tokenServer(t *testing.T, status int, body any, calls *atomic.Int32, forms *[]url.Values) *httptest.Server {
	t.Helper()
	var mu sync.Mutex
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/protocol/openid-connect/token" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		_ = r.ParseForm()
		mu.Lock()
		if forms != nil {
			*forms = append(*forms, r.PostForm)
		}
		mu.Unlock()
		calls.Add(1)
		w.WriteHeader(status)
		_ = json.NewEncoder(w).Encode(body)
	}))
	t.Cleanup(srv.Close)
	return srv
}

func TestTokenSourceReturnsValidTokenWithoutRefresh(t *testing.T) {
	var calls atomic.Int32
	srv := tokenServer(t, 200, TokenResponse{AccessToken: "new"}, &calls, nil)
	creds := Credentials{AccessToken: "old", RefreshToken: "r", Issuer: srv.URL, ClientID: "c", ExpiresAt: time.Now().Add(time.Hour)}
	source := NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")), creds)
	token, err := source.Token(context.Background())
	if err != nil || token != "old" || calls.Load() != 0 {
		t.Fatalf("Token() = %q, %v, %d calls", token, err, calls.Load())
	}
}

func TestTokenSourceRefreshesNearExpiryAndStores(t *testing.T) {
	var calls atomic.Int32
	var forms []url.Values
	srv := tokenServer(t, 200, TokenResponse{AccessToken: "new", RefreshToken: "r2", ExpiresIn: 300}, &calls, &forms)
	store := NewStore(filepath.Join(t.TempDir(), "c.json"))
	// Inside the 30 second margin: not yet expired, but must refresh.
	creds := Credentials{AccessToken: "old", RefreshToken: "r1", Issuer: srv.URL, ClientID: "kalkan-cli", ExpiresAt: time.Now().Add(10 * time.Second)}
	source := NewTokenSource(store, creds)
	token, err := source.Token(context.Background())
	if err != nil || token != "new" {
		t.Fatalf("Token() = %q, %v", token, err)
	}
	if forms[0].Get("grant_type") != "refresh_token" || forms[0].Get("refresh_token") != "r1" || forms[0].Get("client_id") != "kalkan-cli" {
		t.Fatalf("refresh form = %v", forms[0])
	}
	stored, err := store.Load()
	if err != nil || stored.AccessToken != "new" || stored.RefreshToken != "r2" || !stored.ExpiresAt.After(time.Now().Add(4*time.Minute)) {
		t.Fatalf("stored = %+v, %v", stored, err)
	}
	// A second call uses the new token.
	if _, err := source.Token(context.Background()); err != nil || calls.Load() != 1 {
		t.Fatalf("second Token() err %v, calls %d, want 1 call", err, calls.Load())
	}
}

func TestTokenSourceKeepsRefreshTokenWhenNoneReturned(t *testing.T) {
	var calls atomic.Int32
	srv := tokenServer(t, 200, TokenResponse{AccessToken: "new", ExpiresIn: 300}, &calls, nil)
	store := NewStore(filepath.Join(t.TempDir(), "c.json"))
	source := NewTokenSource(store, Credentials{RefreshToken: "keep", Issuer: srv.URL, ClientID: "c"})
	if _, err := source.Token(context.Background()); err != nil {
		t.Fatal(err)
	}
	stored, _ := store.Load()
	if stored.RefreshToken != "keep" {
		t.Fatalf("RefreshToken = %q, want keep", stored.RefreshToken)
	}
}

func TestTokenSourceRefusedRefreshIsNotSignedIn(t *testing.T) {
	for _, status := range []int{http.StatusBadRequest, http.StatusUnauthorized} {
		var calls atomic.Int32
		srv := tokenServer(t, status, map[string]string{"error": "invalid_grant"}, &calls, nil)
		source := NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")), Credentials{RefreshToken: "r", Issuer: srv.URL, ClientID: "c"})
		if _, err := source.Token(context.Background()); !errors.Is(err, ErrNotSignedIn) {
			t.Fatalf("status %d: Token() = %v, want ErrNotSignedIn", status, err)
		}
	}
}

func TestTokenSourceServerErrorAndNetworkErrorAreNotSignedOut(t *testing.T) {
	var calls atomic.Int32
	srv := tokenServer(t, http.StatusInternalServerError, map[string]string{}, &calls, nil)
	source := NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")), Credentials{RefreshToken: "r", Issuer: srv.URL, ClientID: "c"})
	if _, err := source.Token(context.Background()); err == nil || errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("500: Token() = %v, want a plain error", err)
	}

	dead := httptest.NewServer(http.NotFoundHandler())
	deadURL := dead.URL
	dead.Close()
	source = NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")), Credentials{RefreshToken: "r", Issuer: deadURL, ClientID: "c"})
	if _, err := source.Token(context.Background()); err == nil || errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("network error: Token() = %v, want a plain error", err)
	}
}

func TestTokenSourceWithoutRefreshTokenIsNotSignedIn(t *testing.T) {
	source := NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")), Credentials{ExpiresAt: time.Now().Add(-time.Hour)})
	if _, err := source.Token(context.Background()); !errors.Is(err, ErrNotSignedIn) {
		t.Fatalf("Token() = %v, want ErrNotSignedIn", err)
	}
}

func TestTokenSourceRefreshesOnceForParallelCalls(t *testing.T) {
	var calls atomic.Int32
	srv := tokenServer(t, 200, TokenResponse{AccessToken: "new", ExpiresIn: 300}, &calls, nil)
	source := NewTokenSource(NewStore(filepath.Join(t.TempDir(), "c.json")), Credentials{RefreshToken: "r", Issuer: srv.URL, ClientID: "c"})
	var wg sync.WaitGroup
	for range 8 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, err := source.Token(context.Background()); err != nil {
				t.Error(err)
			}
		}()
	}
	wg.Wait()
	if calls.Load() != 1 {
		t.Fatalf("refresh calls = %d, want 1", calls.Load())
	}
}
