package upload

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	pagesRoute       = "/kalkan/pages"
	upsertRoute      = pagesRoute + "/by-path"
	attachmentsRoute = "/storage/attachments"
)

type fakePage struct {
	ID           string
	Path         string
	Version      int64
	Translations map[string]client.KalkanTranslationInput
	Compliance   bool
}

type fakeUpload struct {
	EntityID string
	FileName string
}

// upsertCall is one upsert the fake received, with its dryRun query.
type upsertCall struct {
	Query string
	In    client.KalkanPageUpsert
}

// fakeKalkan is an in-memory Kalkan for the page and attachment endpoints, served by a
// clitest.MockAPI that fails the test on any other request.
type fakeKalkan struct {
	t   *testing.T
	api *clitest.MockAPI

	mu        sync.Mutex
	pages     map[string]*fakePage
	order     []string
	nextID    int
	webEdited map[string]bool
	status    map[string]int // by page path: a status every upsert of that path answers with
	listFails int            // status GET /kalkan/pages answers with when not 0
	getFails  int            // status GET /kalkan/pages/{id} answers with when not 0
	hashes    map[string]bool
	held      map[string]bool
	files     map[string][]client.Attachment
	uploads   []fakeUpload
	upserts   []upsertCall
}

// newFakeKalkan signs in to a mock API in clitest.OrgIDAcme, with no terminal, in a new working
// directory, and serves Kalkan from memory.
func newFakeKalkan(t *testing.T) (*fakeKalkan, string) {
	t.Helper()
	api := clitest.NewSignedInAPI(t)
	f := &fakeKalkan{
		t: t, api: api, pages: map[string]*fakePage{}, webEdited: map[string]bool{}, status: map[string]int{},
		hashes: map[string]bool{}, held: map[string]bool{}, files: map[string][]client.Attachment{},
	}
	api.Handle("GET "+pagesRoute, f.list)
	api.Handle("PUT "+upsertRoute, f.upsert)
	api.Handle("GET "+attachmentsRoute, f.listAttachments)
	api.Handle("POST "+attachmentsRoute, f.addAttachment)
	// kalkan.yaml may set the organization override, which outlives one run in the process.
	session.UseOrganization("")
	t.Cleanup(func() { session.UseOrganization("") })
	work := t.TempDir()
	t.Chdir(work)
	return f, work
}

// seed adds a live page without a call.
func (f *fakeKalkan) seed(path string, version int64, translations ...client.KalkanTranslationInput) *fakePage {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.addLocked(path, version, translations)
}

func (f *fakeKalkan) addLocked(path string, version int64, translations []client.KalkanTranslationInput) *fakePage {
	f.nextID++
	page := &fakePage{ID: "id-" + strconv.Itoa(f.nextID), Path: path, Version: version, Translations: map[string]client.KalkanTranslationInput{}}
	for _, tr := range translations {
		page.Translations[tr.Locale] = tr
	}
	f.pages[path] = page
	f.order = append(f.order, path)
	f.api.Handle("GET "+pagesRoute+"/"+page.ID, f.get)
	return page
}

// page returns the stored page at path, nil when there is none.
func (f *fakeKalkan) page(path string) *fakePage {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.pages[path]
}

// body returns the stored body of the page at path in locale.
func (f *fakeKalkan) body(path, locale string) string {
	f.mu.Lock()
	defer f.mu.Unlock()
	if p := f.pages[path]; p != nil {
		return p.Translations[locale].Body
	}
	return ""
}

func (f *fakeKalkan) uploadCount() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return len(f.uploads)
}

func (f *fakeKalkan) upsertCalls() []upsertCall {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]upsertCall(nil), f.upserts...)
}

// count returns the calls with this method whose path starts with prefix.
func (f *fakeKalkan) count(method, prefix string) int {
	n := 0
	for _, c := range f.api.Calls() {
		if strings.HasPrefix(c, method+" "+prefix) {
			n++
		}
	}
	return n
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

func (f *fakeKalkan) list(w http.ResponseWriter, _ *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.listFails != 0 {
		refuse(w, f.listFails, "", "list failed", nil)
		return
	}
	items := make([]client.KalkanPageListItem, 0, len(f.pages))
	for _, path := range f.order {
		p := f.pages[path]
		item := client.KalkanPageListItem{ID: p.ID, Path: p.Path, Slug: path[strings.LastIndex(path, "/")+1:], Compliance: p.Compliance, Titles: map[string]string{}}
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
	answer(w, http.StatusOK, items)
}

func (f *fakeKalkan) get(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.getFails != 0 {
		refuse(w, f.getFails, "", "get failed", nil)
		return
	}
	id := strings.TrimPrefix(r.URL.Path, pagesRoute+"/")
	for _, p := range f.pages {
		if p.ID == id {
			detail := client.KalkanPageDetail{ID: p.ID, Path: p.Path, Version: p.Version, Level: "EDIT", Compliance: p.Compliance}
			for _, locale := range []string{client.KalkanLocaleEN, client.KalkanLocaleTR} {
				if tr, ok := p.Translations[locale]; ok {
					detail.Translations = append(detail.Translations, client.KalkanPageTranslation{Locale: locale, Title: tr.Title, Body: tr.Body})
				}
			}
			answer(w, http.StatusOK, detail)
			return
		}
	}
	refuse(w, http.StatusNotFound, client.CodeKalkanPageNotFound, "no page", nil)
}

func (f *fakeKalkan) upsert(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		f.t.Errorf("read upsert body: %v", err)
	}
	var in client.KalkanPageUpsert
	if err := json.Unmarshal(body, &in); err != nil {
		f.t.Errorf("bad upsert body %q: %v", body, err)
		refuse(w, http.StatusBadRequest, "", "bad json", nil)
		return
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	f.upserts = append(f.upserts, upsertCall{Query: r.URL.RawQuery, In: in})
	dry := r.URL.Query().Get("dryRun") == "true"
	if status, ok := f.status[in.Path]; ok {
		switch status {
		case http.StatusUnauthorized:
			refuse(w, status, "", "token rejected", nil)
		case http.StatusForbidden:
			refuse(w, status, "KLK_SEC_001", "denied", nil)
		case http.StatusNotFound:
			refuse(w, status, client.CodeKalkanPageNotFound, "hidden", nil)
		default:
			refuse(w, status, "", "server failed", nil)
		}
		return
	}
	if i := strings.LastIndex(in.Path, "/"); i >= 0 && f.pages[in.Path[:i]] == nil {
		refuse(w, http.StatusUnprocessableEntity, "KLK_VAL_001", "the parent page "+in.Path[:i]+" does not exist", nil)
		return
	}
	page := f.pages[in.Path]
	if page == nil {
		if dry {
			answer(w, http.StatusOK, client.KalkanPageUpsertResult{Path: in.Path, Outcome: client.KalkanPageCreated})
			return
		}
		page = f.addLocked(in.Path, 1, in.Translations)
		if in.Compliance != nil {
			page.Compliance = *in.Compliance
		}
		answer(w, http.StatusOK, client.KalkanPageUpsertResult{ID: page.ID, Path: in.Path, Outcome: client.KalkanPageCreated, Version: 1})
		return
	}
	same := true
	for _, tr := range in.Translations {
		if page.Translations[tr.Locale] != tr {
			same = false
		}
	}
	if same {
		answer(w, http.StatusOK, client.KalkanPageUpsertResult{ID: page.ID, Path: in.Path, Outcome: client.KalkanPageUnchanged, Version: page.Version})
		return
	}
	if f.webEdited[in.Path] && !in.Force {
		refuse(w, http.StatusConflict, client.CodeKalkanPageChanged, "The page was edited in Kalkan.",
			map[string]any{"updatedByName": "Ada", "updatedAt": "2026-01-02T03:04:05Z"})
		return
	}
	if dry {
		answer(w, http.StatusOK, client.KalkanPageUpsertResult{ID: page.ID, Path: in.Path, Outcome: client.KalkanPageUpdated, Version: page.Version})
		return
	}
	for _, tr := range in.Translations {
		page.Translations[tr.Locale] = tr
	}
	page.Version++
	delete(f.webEdited, in.Path)
	answer(w, http.StatusOK, client.KalkanPageUpsertResult{ID: page.ID, Path: in.Path, Outcome: client.KalkanPageUpdated, Version: page.Version})
}

func (f *fakeKalkan) listAttachments(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if got := r.URL.Query().Get("entityType"); got != client.StorageEntityTypeKalkanPage {
		f.t.Errorf("entityType = %q", got)
	}
	files := f.files[r.URL.Query().Get("entityId")]
	if files == nil {
		files = []client.Attachment{}
	}
	answer(w, http.StatusOK, files)
}

func (f *fakeKalkan) addAttachment(w http.ResponseWriter, r *http.Request) {
	parts := clitest.ReadMultipartParts(f.t, r)
	var meta []struct {
		FileName   string `json:"fileName"`
		EntityType string `json:"entityType"`
		EntityID   string `json:"entityId"`
	}
	var content []byte
	for _, p := range parts {
		if p.Name == "metadata" {
			if err := json.Unmarshal([]byte(p.Data), &meta); err != nil {
				f.t.Errorf("metadata %q: %v", p.Data, err)
			}
		} else {
			content = []byte(p.Data)
		}
	}
	if len(meta) != 1 || meta[0].EntityType != client.StorageEntityTypeKalkanPage {
		refuse(w, http.StatusBadRequest, "", "bad metadata", nil)
		return
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	digest := sha256Hex(string(content))
	// Like the backend, a Kalkan page refuses content it already holds; other pages may hold
	// their own copy.
	held := meta[0].EntityID + "/" + digest
	if f.hashes[digest] || f.held[held] {
		refuse(w, http.StatusUnprocessableEntity, client.CodeDuplicateContent, "duplicate content", nil)
		return
	}
	f.held[held] = true
	f.uploads = append(f.uploads, fakeUpload{EntityID: meta[0].EntityID, FileName: meta[0].FileName})
	attachment := client.Attachment{
		ID: "att-" + meta[0].EntityID + "-" + digest[:6], FileName: meta[0].FileName,
		URL: f.api.URL() + "/files/" + meta[0].EntityID + "/" + digest[:6] + "/" + meta[0].FileName + "?sig=" + strconv.Itoa(len(f.uploads)),
	}
	f.files[meta[0].EntityID] = append(f.files[meta[0].EntityID], attachment)
	answer(w, http.StatusOK, []client.Attachment{attachment})
}

func en(title, body string) client.KalkanTranslationInput {
	return client.KalkanTranslationInput{Locale: client.KalkanLocaleEN, Title: title, Body: body}
}

func tr(title, body string) client.KalkanTranslationInput {
	return client.KalkanTranslationInput{Locale: client.KalkanLocaleTR, Title: title, Body: body}
}

func sha256Hex(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}

// writeTree writes files (relative path to content) under dir.
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

// result is what one run printed and the exit status the oba root would end with.
type result struct {
	Stdout string
	Stderr string
	Exit   int
	Err    error
}

// run runs "oba kalkan upload <args>" on a fresh tree, as the oba root adds it.
func run(t *testing.T, args ...string) result {
	t.Helper()
	root := clitest.AppRoot("kalkan", NewCmd())
	stdout, stderr, err := clitest.RunCLI(t, root, "", append([]string{"kalkan", "upload"}, args...)...)
	exit := 0
	if err != nil {
		var exitErr *exitcode.Error
		if errors.As(err, &exitErr) {
			exit = exitErr.Code
		} else {
			exit = client.ExitCode(err)
		}
	}
	return result{Stdout: stdout, Stderr: stderr, Exit: exit, Err: err}
}

// reportOut is the --json report.
type reportOut struct {
	Counts map[string]int `json:"counts"`
	Pages  []struct {
		Path    string `json:"path"`
		Outcome string `json:"outcome"`
		Version int64  `json:"version"`
		Error   *struct {
			Code    string `json:"code"`
			Message string `json:"message"`
		} `json:"error"`
	} `json:"pages"`
}

func (p reportOut) outcome(t *testing.T, path string) string {
	t.Helper()
	for _, page := range p.Pages {
		if page.Path == path {
			return page.Outcome
		}
	}
	t.Fatalf("no page %s in report %+v", path, p)
	return ""
}

// runJSON runs the upload with --json on, as the oba root's global flag does, and returns the
// result the root would print on stdout, kept also on a failed run.
func runJSON(t *testing.T, args ...string) (reportOut, result) {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	r := run(t, args...)
	if r.Err != nil && !jsonout.KeptOnFailure() {
		t.Fatalf("a failed run must keep its report: %v", r.Err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	jsonout.Stop()
	var out reportOut
	if err := json.Unmarshal(bytes.TrimSpace(buf.Bytes()), &out); err != nil {
		t.Fatalf("not JSON: %v\n%s", err, buf.String())
	}
	return out, r
}
