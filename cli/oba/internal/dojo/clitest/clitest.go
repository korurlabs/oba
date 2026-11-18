// Package clitest holds what the command tests share: a mock Oba API, an isolated home with a
// stored session, an in-process run of a command tree, typed terminal input and the fixtures
// that the tests of several command packages read. Only tests import it.
package clitest

import (
	"bytes"
	"encoding/csv"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
	"github.com/zalando/go-keyring"

	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

const (
	OrgIDAcme  = "11111111-1111-4111-8111-111111111111"
	OrgIDBeta  = "22222222-2222-4222-8222-222222222222"
	OrgIDBeta2 = "33333333-3333-4333-8333-333333333333"

	// UserMeJSON is GET /users/me of a user in Acme (ADMIN), Beta and a second "beta".
	UserMeJSON = `{"data":{"id":"u1","email":"a@b.c","defaultOrganizationId":"` + OrgIDAcme + `","organizations":[` +
		`{"id":"` + OrgIDAcme + `","name":"Acme","role":"ADMIN","organizationStatus":"ACTIVE"},` +
		`{"id":"` + OrgIDBeta + `","name":"Beta","role":"MEMBER","organizationStatus":"ACTIVE"},` +
		`{"id":"` + OrgIDBeta2 + `","name":"beta","role":"MEMBER","organizationStatus":"ACTIVE"}]}}`
)

// RecordedRequest is one request the mock API received.
type RecordedRequest struct {
	Method string
	Path   string
	Body   string
	Org    string
	Auth   string
}

// MockAPI is an httptest server that routes by "METHOD /path", records every request and
// fails the test on a request no route handles.
type MockAPI struct {
	t      *testing.T
	srv    *httptest.Server
	mu     sync.Mutex
	routes map[string]http.HandlerFunc
	reqs   []RecordedRequest
}

// NewMockAPI starts a mock API that stops when the test ends.
func NewMockAPI(t *testing.T) *MockAPI {
	t.Helper()
	m := &MockAPI{t: t, routes: map[string]http.HandlerFunc{}}
	m.srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		// The route handler may read the body too, for example to parse a multipart upload.
		r.Body = io.NopCloser(bytes.NewReader(body))
		m.mu.Lock()
		m.reqs = append(m.reqs, RecordedRequest{
			Method: r.Method, Path: r.URL.Path, Body: string(body),
			Org: r.Header.Get("X-Organization-ID"), Auth: r.Header.Get("Authorization"),
		})
		h, ok := m.routes[r.Method+" "+r.URL.Path]
		m.mu.Unlock()
		if !ok {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
			http.Error(w, "unexpected", http.StatusInternalServerError)
			return
		}
		h(w, r)
	}))
	t.Cleanup(m.srv.Close)
	return m
}

// URL is the base URL of the mock API.
func (m *MockAPI) URL() string {
	return m.srv.URL
}

// Handle routes "METHOD /path" to h.
func (m *MockAPI) Handle(route string, h http.HandlerFunc) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.routes[route] = h
}

// Respond serves a fixed status and body for a route.
func (m *MockAPI) Respond(route string, status int, body string) {
	m.Handle(route, func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	})
}

// ServeDocs serves the file at path as the API document.
func (m *MockAPI) ServeDocs(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		m.t.Fatal(err)
	}
	m.Respond("GET /v3/api-docs", http.StatusOK, string(data))
}

// Calls returns the recorded requests as "METHOD /path" lines, docs fetches left out.
func (m *MockAPI) Calls() []string {
	m.mu.Lock()
	defer m.mu.Unlock()
	var out []string
	for _, r := range m.reqs {
		if r.Path == "/v3/api-docs" {
			continue
		}
		out = append(out, r.Method+" "+r.Path)
	}
	return out
}

// Requests returns a copy of every recorded request.
func (m *MockAPI) Requests() []RecordedRequest {
	m.mu.Lock()
	defer m.mu.Unlock()
	return append([]RecordedRequest(nil), m.reqs...)
}

// Request returns the first recorded request for the route.
func (m *MockAPI) Request(route string) RecordedRequest {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, r := range m.reqs {
		if r.Method+" "+r.Path == route {
			return r
		}
	}
	m.t.Fatalf("no request %s; got %v", route, m.reqs)
	return RecordedRequest{}
}

// SetupHome gives the test an empty HOME, a mock keyring and no OBA_* variables, OBA_TOKEN
// included.
func SetupHome(t *testing.T) string {
	t.Helper()
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv(config.EnvAPIURL, "")
	t.Setenv(config.EnvIssuer, "")
	t.Setenv(config.EnvClientID, "")
	t.Setenv(config.EnvToken, "")
	keyring.MockInit()
	auth.ResetTokenStore()
	t.Cleanup(auth.ResetTokenStore)
	return home
}

// LoginTo stores a session for apiURL, as 'oba login' would.
func LoginTo(t *testing.T, apiURL, defaultOrgID string) {
	t.Helper()
	err := auth.NewTokenStore().Save(auth.Credentials{
		AccessToken: "access-token", RefreshToken: "refresh-token",
		APIURL: apiURL, IssuerURL: "http://127.0.0.1:1/realms/sensey", ClientID: "oba-cli",
		DefaultOrgID: defaultOrgID,
	})
	if err != nil {
		t.Fatal(err)
	}
}

// RunCLI runs the command tree under root in process with stdin and returns stdout, stderr and
// the error Execute would print. Flags are reset before and after the run, because a command
// tree may be a package variable that outlives one test.
func RunCLI(t *testing.T, root *cobra.Command, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return RunCLIInput(t, root, strings.NewReader(stdin), args...)
}

// RunCLILines runs the command tree like RunCLI, with stdin delivered one line per read as a
// terminal in line mode delivers typed answers. The prompts discard input that waits before they
// ask, so a test that answers several prompts must not hand all its lines over in one read.
func RunCLILines(t *testing.T, root *cobra.Command, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return RunCLIInput(t, root, Typed(strings.SplitAfter(stdin, "\n")...), args...)
}

// RunCLIInput runs the command tree like RunCLI, with in as stdin.
func RunCLIInput(t *testing.T, root *cobra.Command, in io.Reader, args ...string) (string, string, error) {
	t.Helper()
	resetFlags(root)
	var out, errOut bytes.Buffer
	root.SetIn(in)
	root.SetOut(&out)
	root.SetErr(&errOut)
	root.SetArgs(args)
	t.Cleanup(func() {
		root.SetIn(nil)
		root.SetOut(nil)
		root.SetErr(nil)
		root.SetArgs(nil)
		resetFlags(root)
	})
	err := root.Execute()
	return out.String(), errOut.String(), err
}

// resetFlags puts every flag of the tree back to its default. A slice flag is replaced, not set:
// Set appends to it, and its default text "[a,b]" would be parsed as the values "[a" and "b]".
func resetFlags(c *cobra.Command) {
	c.Flags().VisitAll(func(f *pflag.Flag) {
		if sv, ok := f.Value.(pflag.SliceValue); ok {
			_ = sv.Replace(sliceDefault(f.DefValue))
		} else {
			_ = f.Value.Set(f.DefValue)
		}
		f.Changed = false
	})
	for _, sub := range c.Commands() {
		resetFlags(sub)
	}
}

// sliceDefault turns the default text of a slice flag, "[]" or "[a,b]" as pflag writes it as CSV,
// back into its values.
func sliceDefault(def string) []string {
	inner := strings.TrimSuffix(strings.TrimPrefix(def, "["), "]")
	if inner == "" {
		return nil
	}
	values, err := csv.NewReader(strings.NewReader(inner)).Read()
	if err != nil {
		panic("clitest: unreadable slice flag default " + def + ": " + err.Error())
	}
	return values
}
