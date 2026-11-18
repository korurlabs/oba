package cmd

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/korurlabs/kalkan-cli/internal/auth"
	"github.com/korurlabs/kalkan-cli/internal/client"
)

func en(title, body string) client.TranslationInput {
	return client.TranslationInput{Locale: "EN", Title: title, Body: body}
}

func tr(title, body string) client.TranslationInput {
	return client.TranslationInput{Locale: "TR", Title: title, Body: body}
}

type errorShape struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

func TestPagesPrintsTreeBelowItsParent(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 1, en("Engineering", ""))
	api.seed("eng/b", 1, en("B page", ""))
	api.seed("eng/a", 1, en("A page", ""), tr("A sayfa", ""))
	api.seed("eng/a/deep", 1, en("Deep", ""))
	api.pages["eng/a"].Compliance = true

	r := run(t, "pages")
	if r.Exit != 0 || r.Stderr != "" {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
	want := "eng  Engineering  (MANAGE)\n  eng/a  A page  [compliance]\n    eng/a/deep  Deep\n  eng/b  B page\n"
	if r.Stdout != want {
		t.Fatalf("stdout =\n%s\nwant\n%s", r.Stdout, want)
	}
	if api.calls[0].Org != "org-1" {
		t.Fatalf("org header = %q", api.calls[0].Org)
	}
}

func TestPagesSubtreeAndJSON(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 1, en("E", ""))
	api.seed("eng/a", 1, en("A", ""))
	api.seed("eng/ab", 1, en("AB", ""))
	api.seed("eng/a/x", 1, en("X", ""))

	r := run(t, "pages", "/eng/a/", "--json")
	if r.Exit != 0 {
		t.Fatalf("exit %d: %s", r.Exit, r.Stderr)
	}
	var items []client.PageListItem
	decodeJSON(t, r.Stdout, &items)
	if len(items) != 2 || items[0].Path != "eng/a" || items[1].Path != "eng/a/x" {
		t.Fatalf("items = %+v (eng/ab must not match the prefix eng/a)", items)
	}
}

func TestPagesJSONEmptyIsAnArray(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	r := run(t, "pages", "--json")
	if r.Exit != 0 || strings.TrimSpace(r.Stdout) != "[]" {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	if r := run(t, "pages"); r.Stdout != "No pages.\n" {
		t.Fatalf("stdout %q", r.Stdout)
	}
}

func TestPagesUnknownPathExitsWithDenial(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 1, en("E", ""))
	r := run(t, "pages", "nope")
	if r.Exit != ExitDenied || r.Stdout != "" || strings.Count(r.Stderr, "KLK_NOT_001") != 1 {
		t.Fatalf("exit %d stdout %q stderr %q", r.Exit, r.Stdout, r.Stderr)
	}
}

func TestInvalidSlugFailsBeforeAnyCall(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	for _, args := range [][]string{
		{"pages", "bad slug"},
		{"get", "eng/-x"},
		{"put", "eng/ü", "--title", "T", "--file", "-"},
		{"get", "a/b/c/d/e/f/g/h/i/j/k"},
		{"get", "  "},
	} {
		r := run(t, args...)
		if r.Exit != ExitFailure || strings.Count(r.Stderr, "Error:") != 1 {
			t.Fatalf("%v: exit %d stderr %q", args, r.Exit, r.Stderr)
		}
	}
	if api.total() != 0 {
		t.Fatalf("%d calls were made; a bad slug must fail before any", api.total())
	}
}

func TestErrorCodesFromEveryCommand(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 1, en("E", "body"))
	cases := []struct {
		name   string
		status int
		want   int
	}{{"unauthorized", 401, ExitNotSignedIn}, {"forbidden", 403, ExitDenied}, {"hidden", 404, ExitDenied}, {"failure", 500, ExitFailure}}
	for _, tc := range cases {
		for _, args := range [][]string{
			{"pages"},
			{"get", "eng"},
		} {
			api.listFails = tc.status
			r := run(t, args...)
			if r.Exit != tc.want {
				t.Errorf("%s %v: exit %d, want %d", tc.name, args, r.Exit, tc.want)
			}
			if strings.Count(r.Stderr, "\n") != 1 {
				t.Errorf("%s %v: stderr must carry the error once, got %q", tc.name, args, r.Stderr)
			}
		}
		api.listFails = 0
		api.status["eng"] = tc.status
		r := run(t, "put", "eng", "--title", "T", "--file", "-")
		if r.Exit != tc.want || strings.Count(r.Stderr, "\n") != 1 {
			t.Errorf("%s put: exit %d stderr %q, want %d", tc.name, r.Exit, r.Stderr, tc.want)
		}
		delete(api.status, "eng")
	}
}

func TestJSONErrorShapeOnStderr(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.status["eng"] = 403
	r := run(t, "put", "eng", "--title", "T", "--file", "-", "--json")
	if r.Exit != ExitDenied || r.Stdout != "" {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	var e errorShape
	decodeJSON(t, r.Stderr, &e)
	if e.Error.Code != "KLK_SEC_001" || e.Error.Message != "denied" {
		t.Fatalf("error = %+v", e)
	}
}

func TestGetPrintsBodyForLangAndAllOtherwise(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 5, en("Eng", "english body"), tr("Muh", "turkce govde\n"))
	api.pages["eng"].Compliance = true

	r := run(t, "get", "eng", "--lang", "TR")
	if r.Exit != 0 || r.Stdout != "turkce govde\n" {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	if r := run(t, "get", "eng", "--lang", "en"); r.Stdout != "english body\n" {
		t.Fatalf("a body without a final newline gets one: %q", r.Stdout)
	}
	r = run(t, "get", "eng")
	for _, want := range []string{"eng (version 5, your level EDIT, compliance document)", "== EN: Eng (updated 2026-01-02T03:04:05Z by Ada)", "== TR: Muh", "english body", "turkce govde"} {
		if !strings.Contains(r.Stdout, want) {
			t.Errorf("stdout lacks %q:\n%s", want, r.Stdout)
		}
	}
}

func TestGetJSONAndLangFilter(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 5, en("Eng", "b"), tr("Muh", "g"))
	var page client.PageDetail
	r := run(t, "get", "eng", "--json")
	decodeJSON(t, r.Stdout, &page)
	if r.Exit != 0 || page.Version != 5 || len(page.Translations) != 2 {
		t.Fatalf("exit %d page %+v", r.Exit, page)
	}
	r = run(t, "get", "eng", "--json", "--lang", "tr")
	decodeJSON(t, r.Stdout, &page)
	if len(page.Translations) != 1 || page.Translations[0].Locale != "TR" {
		t.Fatalf("translations = %+v", page.Translations)
	}
}

func TestGetFailures(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 1, en("E", "b"))
	if r := run(t, "get", "eng", "--lang", "tr"); r.Exit != ExitFailure || !strings.Contains(r.Stderr, "no tr translation") {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
	if r := run(t, "get", "eng", "--lang", "de"); r.Exit != ExitFailure || !strings.Contains(r.Stderr, "use tr or en") {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
	if r := run(t, "get", "missing"); r.Exit != ExitDenied {
		t.Fatalf("exit %d", r.Exit)
	}
	api.getFails = 403
	if r := run(t, "get", "eng"); r.Exit != ExitDenied {
		t.Fatalf("exit %d", r.Exit)
	}
}

func TestPutCreatesThenRepeatsUnchanged(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	r := runWithInput(t, "# body\n", "put", "eng", "--title", "Eng", "--file", "-", "--lang", "tr", "--compliance")
	if r.Exit != 0 || r.Stdout != "created eng (version 1)\n" {
		t.Fatalf("exit %d stdout %q stderr %q", r.Exit, r.Stdout, r.Stderr)
	}
	inputs, queries := api.puts()
	in := inputs[0]
	if in.Translations[0].Locale != "TR" || in.Translations[0].Body != "# body\n" || in.Compliance == nil || !*in.Compliance || in.Force || queries[0] != "dryRun=false" {
		t.Fatalf("input = %+v query %s", in, queries[0])
	}
	r = runWithInput(t, "# body\n", "put", "eng", "--title", "Eng", "--file", "-", "--lang", "tr", "--json")
	var out putResult
	decodeJSON(t, r.Stdout, &out)
	if r.Exit != 0 || out.Outcome != "unchanged" || out.Version != 1 || out.Path != "eng" {
		t.Fatalf("exit %d out %+v", r.Exit, out)
	}
	inputs, _ = api.puts()
	if inputs[1].Compliance != nil {
		t.Fatal("--compliance left out must keep the stored mark: it must not be sent")
	}
	runWithInput(t, "x", "put", "eng", "--title", "Eng", "--file", "-", "--compliance=false")
	inputs, _ = api.puts()
	if c := inputs[2].Compliance; c == nil || *c {
		t.Fatal("--compliance=false must be sent as false")
	}
}

func TestPutReadsFile(t *testing.T) {
	work := isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	writeTree(t, work, map[string]string{"body.md": "from file"})
	r := run(t, "put", "eng", "--title", "Eng", "--file", "body.md")
	inputs, _ := api.puts()
	if r.Exit != 0 || inputs[0].Translations[0].Body != "from file" {
		t.Fatalf("exit %d inputs %+v", r.Exit, inputs)
	}
}

func TestPutConflictThenForce(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	api.seed("eng", 2, en("Eng", "old"))
	api.webEdited["eng"] = true
	r := runWithInput(t, "new", "put", "eng", "--title", "Eng", "--file", "-")
	if r.Exit != ExitConflict || !strings.Contains(r.Stderr, "Last edited by Ada at 2026-01-02T03:04:05Z. Pass --force to overwrite it.") {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
	if api.pages["eng"].Translations["EN"].Body != "old" {
		t.Fatal("a conflict must leave the page unchanged")
	}
	r = runWithInput(t, "new", "put", "eng", "--title", "Eng", "--file", "-", "--force")
	if r.Exit != 0 || r.Stdout != "updated eng (version 3)\n" {
		t.Fatalf("exit %d stdout %q stderr %q", r.Exit, r.Stdout, r.Stderr)
	}
}

func TestPutArgumentErrors(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	cases := [][]string{
		{"put", "eng", "--file", "-"},
		{"put", "eng", "--title", " ", "--file", "-"},
		{"put", "eng", "--title", "T"},
		{"put", "eng", "--title", "T", "--file", "missing.md"},
		{"put", "eng", "--title", "T", "--file", "-", "--lang", "fr"},
		{"put", "--title", "T", "--file", "-"},
	}
	for _, args := range cases {
		if r := run(t, args...); r.Exit != ExitFailure {
			t.Errorf("%v: exit %d, want 1", args, r.Exit)
		}
	}
	if api.total() != 0 {
		t.Fatalf("%d calls; bad arguments must fail before any", api.total())
	}
}

func TestNotSignedInPromptsForNothing(t *testing.T) {
	isolate(t)
	// The stdin would hang the test if a command read it.
	r := runWithInput(t, "", "pages")
	if r.Exit != ExitNotSignedIn || !strings.Contains(r.Stderr, "kalkan login") || strings.Count(r.Stderr, "\n") != 1 {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
	r = run(t, "pages", "--json")
	var e errorShape
	decodeJSON(t, r.Stderr, &e)
	if e.Error.Code != codeNotSignedIn {
		t.Fatalf("code = %q", e.Error.Code)
	}
}

func TestSignInAtAnotherIssuerIsNotSignedIn(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	r := run(t, "pages", "--issuer", "http://other.invalid/realms/x")
	if r.Exit != ExitNotSignedIn || !strings.Contains(r.Stderr, "signed in at") {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
}

func TestNoOrganizationIsAFailure(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	writeCredentials(t, auth.Credentials{AccessToken: "t", ExpiresAt: time.Now().Add(time.Hour), Issuer: "http://i/realms/sensey", ClientID: "kalkan-cli", APIURL: api.srv.URL})
	r := run(t, "pages")
	if r.Exit != ExitFailure || !strings.Contains(r.Stderr, "no organization chosen") || api.total() != 0 {
		t.Fatalf("exit %d stderr %q calls %d", r.Exit, r.Stderr, api.total())
	}
	if r := run(t, "pages", "--org", "org-flag"); r.Exit != 0 || api.calls[0].Org != "org-flag" {
		t.Fatalf("exit %d", r.Exit)
	}
}

func TestOrganizationSources(t *testing.T) {
	work := isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	if err := os.Mkdir(filepath.Join(work, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	writeTree(t, work, map[string]string{"kalkan.yaml": "organization: org-yaml\n"})
	run(t, "pages")
	if got := api.calls[len(api.calls)-1].Org; got != "org-yaml" {
		t.Fatalf("kalkan.yaml organization = %q, want org-yaml over the stored one", got)
	}
	t.Setenv("KALKAN_ORG_ID", "org-env")
	run(t, "pages")
	if got := api.calls[len(api.calls)-1].Org; got != "org-env" {
		t.Fatalf("env organization = %q", got)
	}
	run(t, "pages", "--org", "org-flag")
	if got := api.calls[len(api.calls)-1].Org; got != "org-flag" {
		t.Fatalf("flag organization = %q", got)
	}
}

func TestInvalidKalkanYAMLFailsSignedInCommands(t *testing.T) {
	work := isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	writeTree(t, work, map[string]string{"kalkan.yaml": "organizaton: typo\n"})
	r := run(t, "pages")
	if r.Exit != ExitFailure || !strings.Contains(r.Stderr, "organizaton") || api.total() != 0 {
		t.Fatalf("exit %d stderr %q calls %d", r.Exit, r.Stderr, api.total())
	}
}

// keycloak fakes the token endpoint of the realm and counts the refreshes.
func keycloak(t *testing.T, status int, body string, refreshes *atomic.Int32) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		refreshes.Add(1)
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv
}

func expiredSignIn(t *testing.T, api *fakeAPI, issuer string) {
	t.Helper()
	writeCredentials(t, auth.Credentials{
		AccessToken: "old", RefreshToken: "ref", ExpiresAt: time.Now().Add(-time.Hour),
		Issuer: issuer, ClientID: "kalkan-cli", APIURL: api.srv.URL, OrgID: "org-1",
	})
}

func TestExpiredTokenIsRefreshedAndStored(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	var refreshes atomic.Int32
	kc := keycloak(t, 200, `{"access_token":"fresh","refresh_token":"ref2","expires_in":300}`, &refreshes)
	expiredSignIn(t, api, kc.URL)
	for range 2 {
		if r := run(t, "pages"); r.Exit != 0 {
			t.Fatalf("exit %d stderr %s", r.Exit, r.Stderr)
		}
	}
	if refreshes.Load() != 1 {
		t.Fatalf("refreshes = %d, want 1 (the second run uses the stored token)", refreshes.Load())
	}
	store, _ := auth.DefaultStore()
	creds, err := store.Load()
	if err != nil || creds.AccessToken != "fresh" || creds.RefreshToken != "ref2" {
		t.Fatalf("stored %+v, %v", creds, err)
	}
}

func TestRefusedRefreshExitsTwoAndNetworkErrorExitsOne(t *testing.T) {
	isolate(t)
	api := newFakeAPI(t)
	var refreshes atomic.Int32
	kc := keycloak(t, 400, `{"error":"invalid_grant"}`, &refreshes)
	expiredSignIn(t, api, kc.URL)
	for _, args := range [][]string{{"pages"}, {"get", "a"}, {"put", "a", "--title", "T", "--file", "-"}, {"push", t.TempDir(), "a"}} {
		r := run(t, args...)
		want := ExitNotSignedIn
		if args[0] == "push" {
			// An empty directory is a local error before sign-in is tried.
			want = ExitFailure
		}
		if r.Exit != want {
			t.Errorf("%v with a refused refresh: exit %d, want %d (stderr %q)", args, r.Exit, want, r.Stderr)
		}
	}
	if api.total() != 0 {
		t.Fatal("a call left without a token")
	}

	kc.Close()
	expiredSignIn(t, api, kc.URL)
	r := run(t, "pages")
	if r.Exit != ExitFailure || strings.Count(r.Stderr, "\n") != 1 {
		t.Fatalf("a network error on refresh: exit %d stderr %q, want 1", r.Exit, r.Stderr)
	}
}

func TestLoginStoresSignInAndLogoutDeletesIt(t *testing.T) {
	isolate(t)
	t.Setenv("PATH", t.TempDir()) // no browser opener can start
	var keycloakURL string
	mux := http.NewServeMux()
	kc := httptest.NewServer(mux)
	t.Cleanup(kc.Close)
	keycloakURL = kc.URL + "/realms/sensey"
	mux.HandleFunc("/realms/sensey/.well-known/openid-configuration", func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(auth.Endpoints{DeviceAuthorizationEndpoint: kc.URL + "/device", TokenEndpoint: kc.URL + "/token"})
	})
	mux.HandleFunc("/device", func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(auth.DeviceAuthorization{DeviceCode: "dc", UserCode: "ABCD", VerificationURI: "http://verify", Interval: 1, ExpiresIn: 60})
	})
	mux.HandleFunc("/token", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"access_token":"tok","refresh_token":"ref","expires_in":300}`))
	})
	oba := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/users/me" || r.Header.Get("Authorization") != "Bearer tok" || r.Header.Get(client.HeaderOrganizationID) != "" {
			t.Errorf("unexpected %s %s auth %q org %q", r.Method, r.URL.Path, r.Header.Get("Authorization"), r.Header.Get(client.HeaderOrganizationID))
		}
		answer(w, 200, map[string]any{"id": "u", "email": "a@b.c", "organizations": []map[string]string{{"id": "org-9", "name": "Korur", "role": "ADMIN"}}})
	}))
	t.Cleanup(oba.Close)

	r := run(t, "login", "--issuer", keycloakURL, "--api-url", oba.URL)
	if r.Exit != 0 || !strings.Contains(r.Stdout, "ABCD") || !strings.Contains(r.Stdout, "Signed in as a@b.c") || !strings.Contains(r.Stdout, "* Korur (ADMIN) org-9") {
		t.Fatalf("exit %d stdout %q stderr %q", r.Exit, r.Stdout, r.Stderr)
	}
	store, _ := auth.DefaultStore()
	creds, err := store.Load()
	if err != nil || creds.OrgID != "org-9" || creds.Issuer != keycloakURL || creds.APIURL != oba.URL || creds.RefreshToken != "ref" {
		t.Fatalf("stored %+v, %v", creds, err)
	}

	if r := run(t, "login", "--issuer", keycloakURL, "--api-url", oba.URL, "--org", "org-other"); r.Exit != ExitFailure || !strings.Contains(r.Stderr, "not a member of organization org-other") {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}

	if r := run(t, "logout"); r.Exit != 0 || r.Stdout != "Signed out.\n" {
		t.Fatalf("logout: exit %d stdout %q", r.Exit, r.Stdout)
	}
	if _, err := store.Load(); err == nil {
		t.Fatal("the sign-in must be gone")
	}
	if r := run(t, "logout"); r.Exit != 0 {
		t.Fatalf("a second logout must succeed, exit %d", r.Exit)
	}
}

func TestChooseOrganization(t *testing.T) {
	one := []auth.Membership{{OrganizationID: "a"}}
	two := []auth.Membership{{OrganizationID: "a"}, {OrganizationID: "b"}}
	cases := []struct {
		asked   string
		members []auth.Membership
		want    string
		wantErr bool
	}{
		{"", one, "a", false}, {"", two, "", false}, {"", nil, "", false}, {"b", two, "b", false}, {"c", two, "", true}, {"a", nil, "", true},
	}
	for _, tc := range cases {
		got, err := chooseOrganization(tc.asked, tc.members)
		if got != tc.want || (err != nil) != tc.wantErr {
			t.Errorf("chooseOrganization(%q, %v) = %q, %v", tc.asked, tc.members, got, err)
		}
	}
}
