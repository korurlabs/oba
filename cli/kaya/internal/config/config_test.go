package config

import "testing"

func TestResolve_order(t *testing.T) {
	t.Setenv(EnvAPIURL, "")
	t.Setenv(EnvIssuer, "")
	t.Setenv(EnvClientID, "")
	t.Setenv(EnvOrgID, "")

	if got := Resolve(Overrides{}, Config{}); got != (Config{APIURL: ProdAPIURL, Issuer: ProdIssuer, ClientID: ClientID}) {
		t.Errorf("defaults = %+v", got)
	}
	stored := Config{APIURL: DevAPIURL, Issuer: DevIssuer, ClientID: "c", OrgID: "stored-org"}
	if got := Resolve(Overrides{}, stored); got != stored {
		t.Errorf("a sign-in against the local stack keeps calling it: %+v", got)
	}
	t.Setenv(EnvOrgID, "env-org")
	t.Setenv(EnvAPIURL, "https://env.example/")
	got := Resolve(Overrides{}, stored)
	if got.OrgID != "env-org" || got.APIURL != "https://env.example" {
		t.Errorf("the environment wins over the sign-in: %+v", got)
	}
	got = Resolve(Overrides{OrgID: "flag-org", Dev: true}, stored)
	if got.OrgID != "flag-org" || got.APIURL != DevAPIURL {
		t.Errorf("the command line wins: %+v", got)
	}
}
