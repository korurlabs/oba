package cmd

import (
	"strings"
	"testing"

	"github.com/korurlabs/kaya-cli/internal/auth"
	"github.com/korurlabs/kaya-cli/internal/config"
)

// storeSignIn writes creds where auth.DefaultStore reads them, under a scratch config directory.
func storeSignIn(t *testing.T, creds auth.Credentials) {
	t.Helper()
	dir := t.TempDir()
	t.Setenv("HOME", dir)
	t.Setenv("XDG_CONFIG_HOME", dir)
	store, err := auth.DefaultStore()
	if err != nil {
		t.Fatal(err)
	}
	if err := store.Save(creds); err != nil {
		t.Fatal(err)
	}
}

func TestSignedInClient_refusesASignInAtAnotherIssuer(t *testing.T) {
	t.Setenv(config.EnvAPIURL, "")
	t.Setenv(config.EnvIssuer, config.ProdIssuer)
	storeSignIn(t, auth.Credentials{AccessToken: "a", Issuer: config.DevIssuer, ClientID: config.ClientID,
		APIURL: config.DevAPIURL})
	if _, _, err := signedInClient(); err == nil || !strings.Contains(err.Error(), "kaya login") {
		t.Fatalf("err = %v, want a request to log in again", err)
	}
}

func TestDeliveryAPIURL_keepsTheAPIURLOfTheSignIn(t *testing.T) {
	t.Setenv(config.EnvAPIURL, "")
	storeSignIn(t, auth.Credentials{AccessToken: "a", Issuer: config.DevIssuer, APIURL: config.DevAPIURL})
	if got := deliveryAPIURL(); got != config.DevAPIURL {
		t.Fatalf("deliveryAPIURL() = %q, want %q", got, config.DevAPIURL)
	}
}
