package session

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// issuer is a Keycloak token endpoint that answers every refresh with new tokens.
func issuer(t *testing.T) (string, *atomic.Int64) {
	t.Helper()
	var refreshes atomic.Int64
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/realms/sensey/protocol/openid-connect/token" || r.FormValue("grant_type") != "refresh_token" {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		refreshes.Add(1)
		_ = json.NewEncoder(w).Encode(map[string]any{"access_token": "fresh", "refresh_token": "fresh-refresh", "expires_in": 300})
	}))
	t.Cleanup(server.Close)
	return server.URL + "/realms/sensey", &refreshes
}

func storeSession(t *testing.T, issuerURL string, expiresAt time.Time) {
	t.Helper()
	err := auth.NewTokenStore().Save(auth.Credentials{
		AccessToken: "stale", RefreshToken: "refresh", ExpiresAt: expiresAt,
		APIURL: "http://api.test", IssuerURL: issuerURL, ClientID: "oba-cli", DefaultOrgID: clitest.OrgIDAcme,
	})
	if err != nil {
		t.Fatal(err)
	}
}

func TestOrganizationBearer_refreshesASessionAboutToExpireOnceAndStoresIt(t *testing.T) {
	clitest.SetupHome(t)
	issuerURL, refreshes := issuer(t)
	storeSession(t, issuerURL, time.Now().Add(10*time.Second))

	bearer, err := OrganizationBearer()
	if err != nil {
		t.Fatal(err)
	}
	for range 3 {
		if token, err := bearer.Token(context.Background()); err != nil || token != "fresh" {
			t.Fatalf("Token() = %q, %v", token, err)
		}
	}
	if refreshes.Load() != 1 {
		t.Fatalf("refreshed %d times, want once", refreshes.Load())
	}
	if bearer.APIURL != "http://api.test" || bearer.OrgID != clitest.OrgIDAcme {
		t.Fatalf("bearer = %+v", bearer)
	}
	stored, err := auth.LoadSession()
	if err != nil || stored.AccessToken != "fresh" || stored.RefreshToken != "fresh-refresh" {
		t.Fatalf("stored = %+v, %v", stored, err)
	}
}

func TestOrganizationBearer_sendsAValidTokenAsItIs(t *testing.T) {
	clitest.SetupHome(t)
	issuerURL, refreshes := issuer(t)
	storeSession(t, issuerURL, time.Now().Add(time.Hour))
	bearer, err := OrganizationBearer()
	if err != nil {
		t.Fatal(err)
	}
	if token, err := bearer.Token(context.Background()); err != nil || token != "stale" || refreshes.Load() != 0 {
		t.Fatalf("Token() = %q, %v after %d refreshes", token, err, refreshes.Load())
	}
}

func TestOrganizationBearer_usesTheEnvironmentToken(t *testing.T) {
	clitest.SetupHome(t)
	t.Setenv(config.EnvToken, "env-token")
	t.Setenv(config.EnvAPIURL, "http://env.test")
	UseOrganization(clitest.OrgIDAcme)
	t.Cleanup(func() { UseOrganization("") })
	bearer, err := OrganizationBearer()
	if err != nil {
		t.Fatal(err)
	}
	token, err := bearer.Token(context.Background())
	if err != nil || token != "env-token" || bearer.APIURL != "http://env.test" || bearer.OrgID != clitest.OrgIDAcme {
		t.Fatalf("bearer = %+v, token %q, %v", bearer, token, err)
	}
}

func TestOrganizationBearer_needsAnOrganization(t *testing.T) {
	clitest.SetupHome(t)
	issuerURL, _ := issuer(t)
	if err := auth.NewTokenStore().Save(auth.Credentials{AccessToken: "a", APIURL: "http://api.test", IssuerURL: issuerURL, ClientID: "oba-cli"}); err != nil {
		t.Fatal(err)
	}
	if _, err := OrganizationBearer(); !errors.Is(err, ErrNoOrganization) {
		t.Fatalf("err = %v, want ErrNoOrganization", err)
	}
}
