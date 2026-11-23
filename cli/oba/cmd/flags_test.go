package cmd

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	eventsPage1 = `{"data":[{"id":"e1","name":"Expo","type":"EXPO","startDate":"2026-05-01","extra":{"kept":1.10}}],` +
		`"pagination":{"currentPage":1,"last":false}}`
	eventsPage2 = `{"data":[{"id":"e2","name":"Meetup","type":"MEETUP","startDate":"2026-06-01"}],` +
		`"pagination":{"currentPage":2,"last":true}}`
)

// signedIn gives the test a stored session on a mock API whose /users/me is the clitest user.
func signedIn(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

// serveEventPages serves two pages of events, chosen by the page parameter.
func serveEventPages(api *clitest.MockAPI) {
	api.Handle("GET /otak/events", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if r.URL.Query().Get("page") == "1" {
			_, _ = w.Write([]byte(eventsPage1))
			return
		}
		_, _ = w.Write([]byte(eventsPage2))
	})
}

func run(t *testing.T, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, rootCmd, "", args...)
}

func TestJSONListPrintsEveryPageAsOneArrayAndNoTable(t *testing.T) {
	api := signedIn(t)
	serveEventPages(api)

	out, errOut, err := run(t, "otak", "event", "list", "--json")
	if err != nil {
		t.Fatal(err)
	}
	var items []map[string]json.RawMessage
	if err := json.Unmarshal([]byte(out), &items); err != nil {
		t.Fatalf("stdout is not a JSON array: %v\n%s", err, out)
	}
	if len(items) != 2 || string(items[0]["id"]) != `"e1"` || string(items[1]["id"]) != `"e2"` {
		t.Fatalf("items = %s", out)
	}
	// A field the CLI has no model for and a number's spelling survive as the API sent them.
	if !strings.Contains(out, `"kept": 1.10`) {
		t.Errorf("raw value changed: %s", out)
	}
	if strings.Contains(out, "NAME") || strings.Contains(out, "Expo  ") {
		t.Errorf("human table on stdout: %s", out)
	}
	if !strings.Contains(errOut, "Expo") {
		t.Errorf("the human table should go to stderr, got %q", errOut)
	}
}

func TestJSONWritePrintsTheResponseData(t *testing.T) {
	api := signedIn(t)
	api.Respond("DELETE /otak/events/e1", http.StatusOK, `{"message":"ok"}`)

	out, _, err := run(t, "otak", "event", "delete", "e1", "--yes", "--json")
	if err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(out) != "null" {
		t.Errorf("stdout = %q, want null for a response without data", out)
	}
}

// The organizations, not the /users/me user they are read from, are the result, with every
// field the API sends, also one the CLI has no model for.
func TestJSONOrgListPrintsTheOrganizations(t *testing.T) {
	for _, args := range [][]string{{"org", "list"}, {"ulak", "org", "list"}} {
		t.Run(strings.Join(args, " "), func(t *testing.T) {
			api := signedIn(t)
			api.Respond("GET /users/me", http.StatusOK, strings.Replace(clitest.UserMeJSON,
				`"organizationStatus":"ACTIVE"}`, `"organizationStatus":"ACTIVE","apps":["KAZAN"]}`, 1))

			out, errOut, err := run(t, append(args, "--json")...)
			if err != nil {
				t.Fatal(err)
			}
			var orgs []map[string]json.RawMessage
			if err := json.Unmarshal([]byte(out), &orgs); err != nil {
				t.Fatalf("stdout is not a JSON array: %v\n%s", err, out)
			}
			if len(orgs) != 3 || string(orgs[0]["id"]) != `"`+clitest.OrgIDAcme+`"` || string(orgs[2]["name"]) != `"beta"` {
				t.Fatalf("stdout = %s", out)
			}
			var apps []string
			if err := json.Unmarshal(orgs[0]["apps"], &apps); err != nil || len(apps) != 1 || apps[0] != "KAZAN" {
				t.Errorf("a field the CLI has no model for was dropped: %s", out)
			}
			if !strings.Contains(errOut, "Acme") {
				t.Errorf("the human table should go to stderr, got %q", errOut)
			}
		})
	}
}

func TestJSONOrgListWithoutOrganizationsPrintsAnEmptyArray(t *testing.T) {
	api := signedIn(t)
	api.Respond("GET /users/me", http.StatusOK, `{"data":{"id":"u1","email":"a@b.c","defaultOrganizationId":null,"organizations":[]}}`)

	out, _, err := run(t, "org", "list", "--json")
	if err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(out) != "[]" {
		t.Errorf("stdout = %q, want []", out)
	}
}

func TestJSONOrgUsePrintsTheChosenOrganization(t *testing.T) {
	signedIn(t)

	out, _, err := run(t, "org", "use", clitest.OrgIDBeta, "--json")
	if err != nil {
		t.Fatal(err)
	}
	var org map[string]json.RawMessage
	if err := json.Unmarshal([]byte(out), &org); err != nil {
		t.Fatalf("stdout is not a JSON object: %v\n%s", err, out)
	}
	if string(org["id"]) != `"`+clitest.OrgIDBeta+`"` || string(org["role"]) != `"MEMBER"` {
		t.Errorf("stdout = %s", out)
	}
}

func TestJSONWithoutAPICallPrintsNull(t *testing.T) {
	signedIn(t)

	out, _, err := run(t, "logout", "--json")
	if err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(out) != "null" {
		t.Errorf("stdout = %q, want null", out)
	}
}

func TestJSONFailedCommandPrintsNoJSON(t *testing.T) {
	api := signedIn(t)
	api.Respond("GET /otak/events", http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)

	out, _, err := run(t, "otak", "event", "list", "--json")
	if err == nil || !strings.Contains(err.Error(), "Insufficient role") {
		t.Fatalf("err = %v", err)
	}
	if out != "" {
		t.Errorf("stdout = %q, want nothing", out)
	}
}

func TestWithoutJSONTheTableStaysOnStdout(t *testing.T) {
	api := signedIn(t)
	serveEventPages(api)
	if _, _, err := run(t, "otak", "event", "list", "--json"); err != nil {
		t.Fatal(err)
	}

	out, _, err := run(t, "otak", "event", "list")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Expo") || strings.HasPrefix(strings.TrimSpace(out), "[") {
		t.Errorf("stdout = %q, want the table", out)
	}
}

func TestOrgFlagActsInAnotherOrganizationOnce(t *testing.T) {
	tests := []struct {
		name, value, wantOrg string
		wantUsersMe          bool
	}{
		{"by id", clitest.OrgIDBeta, clitest.OrgIDBeta, false},
		{"by name, any case", "ACME", clitest.OrgIDAcme, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := signedIn(t)
			serveEventPages(api)
			if err := config.Save(config.File{OrgID: clitest.OrgIDBeta2}); err != nil {
				t.Fatal(err)
			}

			if _, _, err := run(t, "otak", "event", "list", "--org", tt.value); err != nil {
				t.Fatal(err)
			}
			if got := api.Request("GET /otak/events").Org; got != tt.wantOrg {
				t.Errorf("X-Organization-ID = %q, want %q", got, tt.wantOrg)
			}
			if got := contains(api.Calls(), "GET /users/me"); got != tt.wantUsersMe {
				t.Errorf("GET /users/me called = %v, want %v", got, tt.wantUsersMe)
			}
			if f, _ := config.Load(); f.OrgID != clitest.OrgIDBeta2 {
				t.Errorf("stored org = %q, want it unchanged", f.OrgID)
			}
		})
	}
}

func TestOrgFlagRefusesAnAmbiguousOrUnknownName(t *testing.T) {
	tests := []struct{ value, wantErr string }{
		{"beta", `several organizations are named "beta"`},
		{"nope", `no organization of yours matches "nope"`},
	}
	for _, tt := range tests {
		t.Run(tt.value, func(t *testing.T) {
			api := signedIn(t)
			_, _, err := run(t, "otak", "event", "list", "--org", tt.value)
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("err = %v, want %q", err, tt.wantErr)
			}
			if contains(api.Calls(), "GET /otak/events") {
				t.Error("the list was read in no organization the user named")
			}
		})
	}
}

func TestOrgFlagMarksTheOrganizationInOrgList(t *testing.T) {
	signedIn(t)

	out, _, err := run(t, "org", "list", "--org", clitest.OrgIDBeta)
	if err != nil {
		t.Fatal(err)
	}
	for _, line := range strings.Split(out, "\n") {
		if strings.Contains(line, clitest.OrgIDBeta) && !strings.HasPrefix(line, "*") {
			t.Errorf("Beta not marked current: %q", line)
		}
		if strings.Contains(line, clitest.OrgIDAcme) && strings.HasPrefix(line, "*") {
			t.Errorf("Acme still marked current: %q", line)
		}
	}
}

func TestDebugLogsRequestsAndRedactsTheToken(t *testing.T) {
	api := signedIn(t)
	serveEventPages(api)

	out, errOut, err := run(t, "otak", "event", "list", "--debug")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"> GET " + api.URL() + "/otak/events?",
		"> Authorization: Bearer <redacted>",
		"> X-Organization-Id: " + clitest.OrgIDAcme,
		"< 200 OK",
	} {
		if !strings.Contains(errOut, want) {
			t.Errorf("stderr lacks %q:\n%s", want, errOut)
		}
	}
	if strings.Contains(errOut, "access-token") {
		t.Errorf("the token reached the log:\n%s", errOut)
	}
	if strings.Contains(out, "> GET") {
		t.Errorf("the log reached stdout:\n%s", out)
	}

	_, errOut, err = run(t, "otak", "event", "list")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(errOut, "> GET") {
		t.Errorf("logging outlived --debug:\n%s", errOut)
	}
}

func TestEnvTokenSkipsTheStoredSession(t *testing.T) {
	api := signedIn(t)
	serveEventPages(api)
	t.Setenv(config.EnvToken, "env-token")
	t.Setenv(config.EnvAPIURL, api.URL())

	if _, _, err := run(t, "otak", "event", "list"); err != nil {
		t.Fatal(err)
	}
	if got := api.Request("GET /otak/events").Auth; got != "Bearer env-token" {
		t.Errorf("Authorization = %q, want the OBA_TOKEN", got)
	}
}

func TestEnvTokenWithoutSessionUsesTheDefaultOrganization(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	serveEventPages(api)
	t.Setenv(config.EnvToken, "env-token")
	t.Setenv(config.EnvAPIURL, api.URL())

	out, _, err := run(t, "otak", "event", "list", "--json")
	if err != nil {
		t.Fatal(err)
	}
	if got := api.Request("GET /otak/events").Org; got != clitest.OrgIDAcme {
		t.Errorf("X-Organization-ID = %q, want the default organization of /users/me", got)
	}
	// The lookup of the default organization is not the command's result.
	if !strings.HasPrefix(strings.TrimSpace(out), "[") {
		t.Errorf("stdout = %s, want the event list", out)
	}
	for _, r := range api.Requests() {
		if r.Auth != "Bearer env-token" {
			t.Errorf("%s %s sent %q", r.Method, r.Path, r.Auth)
		}
	}
}

func TestEnvTokenUnauthorizedIsNotRefreshed(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusUnauthorized, ``)
	t.Setenv(config.EnvToken, "bad-token")
	t.Setenv(config.EnvAPIURL, api.URL())

	_, _, err := run(t, "org", "list")
	if err == nil || !strings.Contains(err.Error(), "unauthorized") {
		t.Fatalf("err = %v", err)
	}
	if n := len(api.Calls()); n != 1 {
		t.Errorf("calls = %v, want one without a retry", api.Calls())
	}
}

func TestLoginRefusedWhileEnvTokenIsSet(t *testing.T) {
	clitest.SetupHome(t)
	t.Setenv(config.EnvToken, "env-token")

	_, _, err := run(t, "login")
	if err == nil || !strings.Contains(err.Error(), "OBA_TOKEN is set") {
		t.Fatalf("err = %v", err)
	}
}

func contains(list []string, want string) bool {
	for _, item := range list {
		if item == want {
			return true
		}
	}
	return false
}
