package config

import "testing"

func clearEnv(t *testing.T) {
	t.Helper()
	for _, name := range []string{EnvAPIURL, EnvIssuer, EnvClientID, EnvOrgID} {
		t.Setenv(name, "")
	}
}

func TestResolveDefaultsToProduction(t *testing.T) {
	clearEnv(t)
	got := Resolve(Overrides{}, "", Config{})
	want := Config{APIURL: ProdAPIURL, Issuer: ProdIssuer, ClientID: ClientID, OrgID: ""}
	if got != want {
		t.Fatalf("Resolve() = %+v, want %+v", got, want)
	}
}

func TestResolveStoredBeatsDefault(t *testing.T) {
	clearEnv(t)
	stored := Config{APIURL: "http://stored/", Issuer: "http://stored-issuer/", ClientID: "stored-client", OrgID: "org-stored"}
	got := Resolve(Overrides{}, "", stored)
	want := Config{APIURL: "http://stored", Issuer: "http://stored-issuer", ClientID: "stored-client", OrgID: "org-stored"}
	if got != want {
		t.Fatalf("Resolve() = %+v, want %+v", got, want)
	}
}

func TestResolveEnvBeatsStored(t *testing.T) {
	clearEnv(t)
	t.Setenv(EnvAPIURL, "http://env")
	t.Setenv(EnvIssuer, "http://env-issuer")
	t.Setenv(EnvClientID, "env-client")
	t.Setenv(EnvOrgID, "org-env")
	stored := Config{APIURL: "http://stored", Issuer: "http://stored-issuer", ClientID: "stored-client", OrgID: "org-stored"}
	got := Resolve(Overrides{}, "org-project", stored)
	want := Config{APIURL: "http://env", Issuer: "http://env-issuer", ClientID: "env-client", OrgID: "org-env"}
	if got != want {
		t.Fatalf("Resolve() = %+v, want %+v", got, want)
	}
}

func TestResolveOrganizationOrder(t *testing.T) {
	clearEnv(t)
	stored := Config{OrgID: "org-stored"}
	if got := Resolve(Overrides{}, "org-project", stored).OrgID; got != "org-project" {
		t.Fatalf("project organization = %q, want org-project (beats stored)", got)
	}
	t.Setenv(EnvOrgID, "org-env")
	if got := Resolve(Overrides{}, "org-project", stored).OrgID; got != "org-env" {
		t.Fatalf("env organization = %q, want org-env (beats project)", got)
	}
	if got := Resolve(Overrides{OrgID: "org-flag"}, "org-project", stored).OrgID; got != "org-flag" {
		t.Fatalf("flag organization = %q, want org-flag", got)
	}
}

func TestResolveFlagBeatsDevAndEnv(t *testing.T) {
	clearEnv(t)
	t.Setenv(EnvAPIURL, "http://env")
	o := Overrides{APIURL: "http://flag/", Issuer: "http://flag-issuer", ClientID: "flag-client", Dev: true}
	got := Resolve(o, "", Config{})
	if got.APIURL != "http://flag" || got.Issuer != "http://flag-issuer" || got.ClientID != "flag-client" {
		t.Fatalf("Resolve() = %+v, flags must win over dev and env", got)
	}
}

func TestResolveDevBeatsEnvAndStored(t *testing.T) {
	clearEnv(t)
	t.Setenv(EnvAPIURL, "http://env")
	t.Setenv(EnvIssuer, "http://env-issuer")
	got := Resolve(Overrides{Dev: true}, "", Config{APIURL: "http://stored", Issuer: "http://stored-issuer"})
	if got.APIURL != DevAPIURL || got.Issuer != DevIssuer {
		t.Fatalf("Resolve() = %+v, want the local stack", got)
	}
}

func TestResolveIgnoresBlankValues(t *testing.T) {
	clearEnv(t)
	t.Setenv(EnvAPIURL, "   ")
	got := Resolve(Overrides{APIURL: " "}, " ", Config{APIURL: "  http://stored  "})
	if got.APIURL != "http://stored" {
		t.Fatalf("APIURL = %q, want http://stored", got.APIURL)
	}
	if got.OrgID != "" {
		t.Fatalf("OrgID = %q, want empty", got.OrgID)
	}
}
