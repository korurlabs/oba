package cmd

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"

	"github.com/korurlabs/kalkan-cli/internal/auth"
	"github.com/korurlabs/kalkan-cli/internal/client"
	"github.com/korurlabs/kalkan-cli/internal/config"
)

// call is one request the fake API received.
type call struct {
	Method string
	Path   string
	Query  string
	Org    string
	Body   []byte
}

type fakePage struct {
	ID           string
	Path         string
	Version      int64
	Translations map[string]client.TranslationInput
	Compliance   bool
	Position     int
}

type upload struct {
	EntityID string
	FileName string
}

// fakeAPI is an in-memory Oba API for the page and attachment endpoints.
type fakeAPI struct {
	t   *testing.T
	srv *httptest.Server

	mu        sync.Mutex
	calls     []call
	pages     map[string]*fakePage
	order     []string
	nextID    int
	webEdited map[string]bool
	status    map[string]int                 // by page path: a status every PUT of that path answers with
	listFails int                            // status answered by GET /kalkan/pages when not 0
	getFails  int                            // status answered by GET /kalkan/pages/{id} when not 0
	hashes    map[string]bool                // content every upload is refused for, whatever the page
	held      map[string]bool                // page id and content hash of every stored attachment
	files     map[string][]client.Attachment // by page id
	uploads   []upload
}

func newFakeAPI(t *testing.T) *fakeAPI {
	t.Helper()
	f := &fakeAPI{
		t: t, pages: map[string]*fakePage{}, webEdited: map[string]bool{}, status: map[string]int{},
		hashes: map[string]bool{}, held: map[string]bool{}, files: map[string][]client.Attachment{},
	}
	f.srv = httptest.NewServer(http.HandlerFunc(f.serve))
	t.Cleanup(f.srv.Close)
	return f
}

// seed adds a live page without a call.
func (f *fakeAPI) seed(path string, version int64, translations ...client.TranslationInput) *fakePage {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.addLocked(path, version, translations)
}

func (f *fakeAPI) addLocked(path string, version int64, translations []client.TranslationInput) *fakePage {
	f.nextID++
	page := &fakePage{ID: "id-" + strconv.Itoa(f.nextID), Path: path, Version: version, Translations: map[string]client.TranslationInput{}}
	for _, tr := range translations {
		page.Translations[tr.Locale] = tr
	}
	f.pages[path] = page
	f.order = append(f.order, path)
	return page
}

func (f *fakeAPI) record(r *http.Request, body []byte) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.calls = append(f.calls, call{Method: r.Method, Path: r.URL.Path, Query: r.URL.RawQuery, Org: r.Header.Get(client.HeaderOrganizationID), Body: body})
}

// count returns the calls with this method whose path starts with prefix.
func (f *fakeAPI) count(method, prefix string) int {
	f.mu.Lock()
	defer f.mu.Unlock()
	n := 0
	for _, c := range f.calls {
		if c.Method == method && strings.HasPrefix(c.Path, prefix) {
			n++
		}
	}
	return n
}

func (f *fakeAPI) total() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return len(f.calls)
}

// puts returns the upsert bodies, in order, with their dryRun query.
func (f *fakeAPI) puts() ([]client.UpsertInput, []string) {
	f.mu.Lock()
	defer f.mu.Unlock()
	var inputs []client.UpsertInput
	var queries []string
	for _, c := range f.calls {
		if c.Method == http.MethodPut && c.Path == "/kalkan/pages/by-path" {
			var in client.UpsertInput
			if err := json.Unmarshal(c.Body, &in); err != nil {
				f.t.Errorf("bad upsert body: %v", err)
			}
			inputs = append(inputs, in)
			queries = append(queries, c.Query)
		}
	}
	return inputs, queries
}

func answer(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{"success": status < 300, "data": data})
}

func refuse(w http.ResponseWriter, status int, code, message string, values map[string]any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": false,
		"error":   map[string]any{"code": code, "message": message, "errorValues": values},
	})
}

func (f *fakeAPI) serve(w http.ResponseWriter, r *http.Request) {
	body, _ := io.ReadAll(r.Body)
	f.record(r, body)
	f.mu.Lock()
	defer f.mu.Unlock()
	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/kalkan/pages":
		if f.listFails != 0 {
			refuse(w, f.listFails, "", "list failed", nil)
			return
		}
		items := make([]client.PageListItem, 0, len(f.pages))
		for _, path := range f.order {
			p := f.pages[path]
			if p == nil {
				continue
			}
			item := client.PageListItem{ID: p.ID, Path: p.Path, Slug: path[strings.LastIndex(path, "/")+1:], Position: p.Position, Compliance: p.Compliance, Titles: map[string]string{}}
			if i := strings.LastIndex(path, "/"); i >= 0 {
				if parent := f.pages[path[:i]]; parent != nil {
					item.ParentID = parent.ID
				}
			} else {
				item.Level = "MANAGE"
			}
			for locale, tr := range p.Translations {
				item.Titles[locale] = tr.Title
			}
			items = append(items, item)
		}
		answer(w, 200, items)
	case r.Method == http.MethodGet && strings.HasPrefix(r.URL.Path, "/kalkan/pages/"):
		if f.getFails != 0 {
			refuse(w, f.getFails, "", "get failed", nil)
			return
		}
		id := strings.TrimPrefix(r.URL.Path, "/kalkan/pages/")
		for _, p := range f.pages {
			if p.ID == id {
				detail := client.PageDetail{ID: p.ID, Path: p.Path, Version: p.Version, Level: "EDIT", Compliance: p.Compliance}
				for _, locale := range []string{"EN", "TR"} {
					if tr, ok := p.Translations[locale]; ok {
						detail.Translations = append(detail.Translations, client.PageTranslation{Locale: locale, Title: tr.Title, Body: tr.Body, UpdatedAt: "2026-01-02T03:04:05Z", UpdatedByName: "Ada"})
					}
				}
				answer(w, 200, detail)
				return
			}
		}
		refuse(w, 404, client.CodePageNotFound, "no page", nil)
	case r.Method == http.MethodPut && r.URL.Path == "/kalkan/pages/by-path":
		f.upsertLocked(w, r, body)
	case r.Method == http.MethodGet && r.URL.Path == "/storage/attachments":
		answer(w, 200, f.files[r.URL.Query().Get("entityId")])
	case r.Method == http.MethodPost && r.URL.Path == "/storage/attachments":
		f.uploadLocked(w, r, body)
	default:
		refuse(w, 404, "", "unknown route "+r.Method+" "+r.URL.Path, nil)
	}
}

func (f *fakeAPI) upsertLocked(w http.ResponseWriter, r *http.Request, body []byte) {
	var in client.UpsertInput
	if err := json.Unmarshal(body, &in); err != nil {
		refuse(w, 400, "", "bad json", nil)
		return
	}
	dry := r.URL.Query().Get("dryRun") == "true"
	if status, ok := f.status[in.Path]; ok {
		switch status {
		case 401:
			refuse(w, 401, "", "token rejected", nil)
		case 403:
			refuse(w, 403, "KLK_SEC_001", "denied", nil)
		case 404:
			refuse(w, 404, client.CodePageNotFound, "hidden", nil)
		default:
			refuse(w, status, "", "server failed", nil)
		}
		return
	}
	if i := strings.LastIndex(in.Path, "/"); i >= 0 && f.pages[in.Path[:i]] == nil {
		refuse(w, 422, "KLK_VAL_001", "the parent page "+in.Path[:i]+" does not exist", nil)
		return
	}
	page := f.pages[in.Path]
	if page == nil {
		if dry {
			answer(w, 200, client.UpsertResult{Path: in.Path, Outcome: client.OutcomeCreated})
			return
		}
		page = f.addLocked(in.Path, 1, in.Translations)
		if in.Compliance != nil {
			page.Compliance = *in.Compliance
		}
		answer(w, 200, client.UpsertResult{ID: page.ID, Path: in.Path, Outcome: client.OutcomeCreated, Version: 1})
		return
	}
	same := true
	for _, tr := range in.Translations {
		if page.Translations[tr.Locale] != tr {
			same = false
		}
	}
	if same {
		answer(w, 200, client.UpsertResult{ID: page.ID, Path: in.Path, Outcome: client.OutcomeUnchanged, Version: page.Version})
		return
	}
	if f.webEdited[in.Path] && !in.Force {
		refuse(w, 409, client.CodeChangedSincePush, "The page was edited in Kalkan.", map[string]any{"updatedByName": "Ada", "updatedAt": "2026-01-02T03:04:05Z"})
		return
	}
	if dry {
		answer(w, 200, client.UpsertResult{ID: page.ID, Path: in.Path, Outcome: client.OutcomeUpdated, Version: page.Version})
		return
	}
	for _, tr := range in.Translations {
		page.Translations[tr.Locale] = tr
	}
	page.Version++
	delete(f.webEdited, in.Path)
	answer(w, 200, client.UpsertResult{ID: page.ID, Path: in.Path, Outcome: client.OutcomeUpdated, Version: page.Version})
}

func (f *fakeAPI) uploadLocked(w http.ResponseWriter, r *http.Request, body []byte) {
	_, params, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil {
		refuse(w, 400, "", "bad content type", nil)
		return
	}
	reader := multipart.NewReader(bytes.NewReader(body), params["boundary"])
	var meta []struct {
		FileName string `json:"fileName"`
		EntityID string `json:"entityId"`
	}
	var content []byte
	for {
		part, err := reader.NextPart()
		if err != nil {
			break
		}
		data, _ := io.ReadAll(part)
		if part.FormName() == "metadata" {
			_ = json.Unmarshal(data, &meta)
		} else {
			content = data
		}
	}
	if len(meta) != 1 {
		refuse(w, 400, "", "bad metadata", nil)
		return
	}
	sum := sha256.Sum256(content)
	digest := hex.EncodeToString(sum[:])
	// Like the backend, a Kalkan page refuses content it already holds; other pages may hold
	// their own copy.
	held := meta[0].EntityID + "/" + digest
	if f.hashes[digest] || f.held[held] {
		refuse(w, 422, client.CodeDuplicateContent, "duplicate content", nil)
		return
	}
	f.held[held] = true
	f.uploads = append(f.uploads, upload{EntityID: meta[0].EntityID, FileName: meta[0].FileName})
	attachment := client.Attachment{ID: "att-" + meta[0].EntityID + "-" + digest[:6], FileName: meta[0].FileName, URL: f.srv.URL + "/files/" + meta[0].EntityID + "/" + digest[:6] + "/" + meta[0].FileName + "?sig=" + strconv.Itoa(len(f.uploads))}
	f.files[meta[0].EntityID] = append(f.files[meta[0].EntityID], attachment)
	answer(w, 200, []client.Attachment{attachment})
}

// writeCredentials stores a sign-in for the CLI under a private config directory and returns it.
func writeCredentials(t *testing.T, creds auth.Credentials) {
	t.Helper()
	store, err := auth.DefaultStore()
	if err != nil {
		t.Fatal(err)
	}
	if err := store.Save(creds); err != nil {
		t.Fatal(err)
	}
}

// isolate points the config directory and the working directory at new directories, and clears
// the KALKAN_* variables, so a test never reads the developer's own sign-in.
func isolate(t *testing.T) string {
	t.Helper()
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("XDG_CONFIG_HOME", filepath.Join(home, "config"))
	for _, name := range []string{config.EnvAPIURL, config.EnvIssuer, config.EnvClientID, config.EnvOrgID} {
		t.Setenv(name, "")
	}
	work := filepath.Join(home, "work")
	if err := os.MkdirAll(work, 0o750); err != nil {
		t.Fatal(err)
	}
	t.Chdir(work)
	return work
}

// signIn stores a valid sign-in against the fake API and returns the issuer it was made at.
func signIn(t *testing.T, api *fakeAPI) string {
	t.Helper()
	const issuer = "http://issuer.invalid/realms/sensey"
	writeCredentials(t, auth.Credentials{
		AccessToken: "tok", RefreshToken: "ref", ExpiresAt: time.Now().Add(time.Hour),
		Issuer: issuer, ClientID: config.ClientID, APIURL: api.srv.URL, OrgID: "org-1",
	})
	return issuer
}

// result is what one run of the CLI printed and returned.
type result struct {
	Stdout string
	Stderr string
	Exit   int
}

// resetFlags returns every flag of the command tree to its default, since the commands share
// package-level flag variables between runs of one test binary.
func resetFlags(c *cobra.Command) {
	reset := func(f *pflag.Flag) {
		if err := f.Value.Set(f.DefValue); err != nil {
			panic(err)
		}
		f.Changed = false
	}
	c.Flags().VisitAll(reset)
	c.PersistentFlags().VisitAll(reset)
	for _, sub := range c.Commands() {
		resetFlags(sub)
	}
}

// setContext gives every command the context of this run: cobra keeps the first context a
// subcommand received, which the previous run has already canceled.
func setContext(c *cobra.Command, ctx context.Context) {
	c.SetContext(ctx)
	for _, sub := range c.Commands() {
		setContext(sub, ctx)
	}
}

// run executes the CLI as main does and returns what it printed and its exit code.
func run(t *testing.T, args ...string) result {
	t.Helper()
	return runWithInput(t, "", args...)
}

func runWithInput(t *testing.T, stdin string, args ...string) result {
	t.Helper()
	resetFlags(rootCmd)
	var stdout, stderr bytes.Buffer
	rootCmd.SetOut(&stdout)
	rootCmd.SetErr(&stderr)
	rootCmd.SetIn(strings.NewReader(stdin))
	rootCmd.SetArgs(args)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	setContext(rootCmd, ctx)
	err := rootCmd.ExecuteContext(ctx)
	exit := 0
	if err != nil {
		var exitErr *ExitError
		if errors.As(err, &exitErr) {
			exit = exitErr.Code
		} else {
			_, _ = fmt.Fprintln(&stderr, "Error:", err)
			exit = ExitFailure
		}
	}
	return result{Stdout: stdout.String(), Stderr: stderr.String(), Exit: exit}
}

// decodeJSON parses s into v or fails the test.
func decodeJSON(t *testing.T, s string, v any) {
	t.Helper()
	if err := json.Unmarshal([]byte(s), v); err != nil {
		t.Fatalf("not JSON: %v\n%s", err, s)
	}
}

// writeTree writes files under dir.
func writeTree(t *testing.T, dir string, files map[string]string) {
	t.Helper()
	for name, content := range files {
		full := filepath.Join(dir, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(full), 0o750); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(full, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
}

func sha256Hex(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}
