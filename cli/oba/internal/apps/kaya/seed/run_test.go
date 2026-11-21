package seed

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// fakeKaya answers the seed endpoints like the backend: a seed naming a blob it does not hold
// is 422 KAY_CFG_009 with the missing hashes; uploads are hashed again.
type fakeKaya struct {
	mu        sync.Mutex
	blobs     map[string]bool
	uploads   []string
	seedPosts int
	release   client.Release
	headers   []http.Header
}

func (f *fakeKaya) handler(t *testing.T) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		f.mu.Lock()
		defer f.mu.Unlock()
		f.headers = append(f.headers, r.Header.Clone())
		switch {
		case r.Method == http.MethodPut && strings.HasPrefix(r.URL.Path, "/kaya/blobs/"):
			sha := strings.TrimPrefix(r.URL.Path, "/kaya/blobs/")
			body, _ := io.ReadAll(r.Body)
			digest := sha256.Sum256(body)
			if hex.EncodeToString(digest[:]) != sha {
				answer(w, http.StatusBadRequest, nil, &errorBody{Code: "KAY_CFG_005", Message: "mismatch"})
				return
			}
			f.blobs[sha] = true
			f.uploads = append(f.uploads, sha)
			answer(w, http.StatusOK, client.BlobUpload{SHA256: sha, Size: len(body), Stored: true}, nil)
		case r.Method == http.MethodPost && r.URL.Path == "/kaya/seed-runs":
			f.seedPosts++
			var req client.SeedRequest
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				t.Errorf("seed body: %v", err)
			}
			var missing []string
			for _, set := range req.Sets {
				for _, file := range set.Files {
					if file.SHA256 != "" && !f.blobs[file.SHA256] {
						missing = append(missing, file.SHA256)
					}
				}
			}
			if len(missing) > 0 {
				answer(w, http.StatusUnprocessableEntity, nil, &errorBody{Code: "KAY_CFG_009", Message: "missing",
					ErrorValues: map[string]any{"missing": missing}})
				return
			}
			sets := make([]client.SeedSetReport, 0, len(req.Sets))
			for _, set := range req.Sets {
				sets = append(sets, client.SeedSetReport{PackID: "pack-" + set.Name, Kind: set.Kind, Name: set.Name, Outcome: "CREATED", FileCount: len(set.Files)})
			}
			answer(w, http.StatusOK, client.SeedRun{ID: "run-1", SourceRef: req.SourceRef, Status: client.SeedPassed,
				Report: client.SeedReport{Applied: true, Sets: sets,
					Releases: []client.SeedReleaseReport{{RepositoryID: "repo-1", Number: f.release.Number}}}}, nil)
		case r.Method == http.MethodGet && r.URL.Path == "/kaya/repositories":
			// The server stores the normalised remote.
			answer(w, http.StatusOK, []client.Repository{{ID: "repo-1", Name: "Village", RemoteURL: "github.com/senseylabs/village", Status: "ACTIVE"}}, nil)
		case r.Method == http.MethodGet && r.URL.Path == "/kaya/repositories/repo-1/releases/1":
			answer(w, http.StatusOK, f.release, nil)
		default:
			t.Errorf("unexpected %s %s", r.Method, r.URL)
			http.NotFound(w, r)
		}
	})
}

type errorBody struct {
	Code        string         `json:"code"`
	Message     string         `json:"message"`
	ErrorValues map[string]any `json:"errorValues,omitempty"`
}

func answer(w http.ResponseWriter, status int, data any, failure *errorBody) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{"success": failure == nil, "message": "", "data": data, "error": failure})
}

// releaseOf is the release the server would compose from the plan: every set's files.
func releaseOf(plan Plan) client.Release {
	release := client.Release{Number: 1, RepositoryID: "repo-1"}
	for _, set := range plan.Request.Sets {
		for _, file := range set.Files {
			release.Files = append(release.Files, client.ReleaseEntry{Path: file.Path, SHA256: file.SHA256, Mode: file.Mode, Target: file.SymlinkTarget})
		}
	}
	return release
}

func TestSend_uploadsOnlyMissingBlobsThenPasses(t *testing.T) {
	agents, repo := fixture(t)
	// Bring the managed file back in line, so the release can equal the disk.
	write(t, repo, ".claude/agents/coding-engineer.md", "engineer\n", 0o644)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	held := sum("core\n")
	fake := &fakeKaya{blobs: map[string]bool{held: true}, release: releaseOf(plan)}
	server := httptest.NewServer(fake.handler(t))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })

	run, uploaded, err := Send(context.Background(), api, plan)
	if err != nil {
		t.Fatal(err)
	}
	if fake.seedPosts != 2 {
		t.Errorf("seed posts = %d, want 2 (refused for missing blobs, then accepted)", fake.seedPosts)
	}
	if uploaded != len(plan.Blobs)-1 || len(fake.uploads) != uploaded {
		t.Errorf("uploaded %d (%d at the server), want %d", uploaded, len(fake.uploads), len(plan.Blobs)-1)
	}
	for _, sha := range fake.uploads {
		if sha == held {
			t.Errorf("a blob the server holds was uploaded again")
		}
	}
	for _, header := range fake.headers {
		if header.Get("Authorization") != "Bearer jwt" || header.Get(client.HeaderOrganizationID) != "org-1" {
			t.Fatalf("headers = %v", header)
		}
	}

	checks, err := Check(context.Background(), api, plan, run)
	if err != nil {
		t.Fatal(err)
	}
	if len(checks) != 1 || len(checks[0].Mismatches) != 0 || checks[0].Skipped != "" {
		t.Fatalf("checks = %+v, want the repository equal to its release", checks)
	}
	if !Passed(run, checks) {
		t.Errorf("a PASSED run whose release equals the disk passes")
	}

	// A second run with everything held uploads nothing.
	run, uploaded, err = Send(context.Background(), api, plan)
	if err != nil || uploaded != 0 || fake.seedPosts != 3 {
		t.Errorf("second run: uploaded %d, posts %d, err %v", uploaded, fake.seedPosts, err)
	}
	if run.Status != client.SeedPassed {
		t.Errorf("status = %s", run.Status)
	}
}

func TestCheck_releaseThatDiffersFails(t *testing.T) {
	agents, repo := fixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	// The pack holds the source subagent; the disk holds the drifted copy.
	fake := &fakeKaya{blobs: map[string]bool{}, release: releaseOf(plan)}
	server := httptest.NewServer(fake.handler(t))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
	run, _, err := Send(context.Background(), api, plan)
	if err != nil {
		t.Fatal(err)
	}
	checks, err := Check(context.Background(), api, plan, run)
	if err != nil {
		t.Fatal(err)
	}
	if len(checks) != 1 || len(checks[0].Mismatches) != 1 || checks[0].Mismatches[0].Path != ".claude/agents/coding-engineer.md" {
		t.Fatalf("checks = %+v, want the drifted subagent", checks)
	}
	if Passed(run, checks) {
		t.Errorf("a release that differs from the disk does not pass")
	}
}

func TestPassed_failedRunFails(t *testing.T) {
	if Passed(client.SeedRun{Status: client.SeedFailed}, nil) {
		t.Error("a FAILED run does not pass")
	}
}

func TestSend_otherRefusalIsReturned(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		answer(w, http.StatusForbidden, nil, &errorBody{Code: "GEN_SEC_003", Message: "OWNER required"})
	}))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
	_, _, err := Send(context.Background(), api, Plan{Request: client.SeedRequest{SourceRef: "x"}})
	if !client.IsCode(err, "GEN_SEC_003") {
		t.Fatalf("err = %v, want GEN_SEC_003", err)
	}
	if !strings.Contains(err.Error(), "OWNER required") {
		t.Errorf("the message reaches the user: %v", err)
	}
}

func TestSend_packRefusalsNameTheirReason(t *testing.T) {
	for name, refusal := range map[string]struct {
		status int
		body   errorBody
		want   string
	}{
		"stored pack with another parent": {http.StatusConflict,
			errorBody{Code: "KAY_CFG_033", Message: "The pack exists with another parent.", ErrorValues: map[string]any{"name": "kagi", "parent": "org", "storedParentPackId": "p-village"}},
			"Kaya refused the seed run (409 KAY_CFG_033): The pack exists with another parent.\n  name: kagi\n  parent: org\n  storedParentPackId: p-village"},
		"mounted rule not scoped": {http.StatusUnprocessableEntity,
			errorBody{Code: "KAY_CFG_034", Message: "A rule of a pack mounted in a folder needs paths frontmatter whose every pattern lies under that folder.",
				ErrorValues: map[string]any{"rule": ".agents/rules/kagi/api.md", "folder": "projects/kagi", "pack": "p-kagi"}},
			"\n  folder: projects/kagi\n  pack: p-kagi\n  rule: .agents/rules/kagi/api.md"},
		"overlapping folder": {http.StatusUnprocessableEntity,
			errorBody{Code: "KAY_CFG_026", Message: "The mount folder is not valid.", ErrorValues: map[string]any{"rootPath": "projects lies around projects/kagi"}},
			"(422 KAY_CFG_026): The mount folder is not valid.\n  rootPath: projects lies around projects/kagi"},
	} {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			body := refusal.body
			answer(w, refusal.status, nil, &body)
		}))
		api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
		_, _, err := Send(context.Background(), api, Plan{Request: client.SeedRequest{SourceRef: "x"}})
		server.Close()

		if !client.IsCode(err, refusal.body.Code) {
			t.Errorf("%s: err = %v, want %s", name, err, refusal.body.Code)
			continue
		}
		if !strings.Contains(err.Error(), refusal.want) {
			t.Errorf("%s: the error\n%s\nlacks\n%s", name, err, refusal.want)
		}
	}
}

func TestCheck_repositoryKayaDoesNotKnowIsSkipped(t *testing.T) {
	agents, repo := fixture(t)
	git(t, repo, "remote", "set-url", "origin", "git@github.com:senseylabs/other.git")
	writeManaged(t, repo, []string{"org"})
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	fake := &fakeKaya{blobs: map[string]bool{}, release: releaseOf(plan)}
	server := httptest.NewServer(fake.handler(t))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
	run, _, err := Send(context.Background(), api, plan)
	if err != nil {
		t.Fatal(err)
	}
	checks, err := Check(context.Background(), api, plan, run)
	if err != nil {
		t.Fatal(err)
	}
	if len(checks) != 1 || !strings.Contains(checks[0].Skipped, "no repository with the remote") {
		t.Fatalf("checks = %+v", checks)
	}
	if Passed(run, checks) {
		t.Error("a repository that was not compared does not pass")
	}
}

func TestPrintRun_showsTheSizeWarningsKayaReturns(t *testing.T) {
	// The report as the backend writes it: every warning names the set of its file.
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		answer(w, http.StatusOK, map[string]any{
			"id": "run-1", "sourceRef": "abc", "status": "PASSED",
			"report": map[string]any{
				"applied": true,
				"warnings": []any{
					map[string]any{"setKind": "PACK", "setName": "org", "violation": map[string]any{
						"path": ".agents/skills/big/SKILL.md", "rule": "SKILL_TOO_LONG", "message": "SKILL.md has 1512 lines; it must stay under 1500"}},
				},
			},
		}, nil)
	}))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
	run, err := api.CreateSeedRun(context.Background(), client.SeedRequest{SourceRef: "abc"})
	if err != nil {
		t.Fatal(err)
	}
	var out strings.Builder
	PrintRun(&out, run, []string{"a local note"}, nil)
	for _, want := range []string{
		"Size limits broken, imported anyway (1):",
		"  org: SKILL_TOO_LONG at .agents/skills/big/SKILL.md (SKILL.md has 1512 lines; it must stay under 1500)\n",
		"Warnings:\n  a local note",
	} {
		if !strings.Contains(out.String(), want) {
			t.Errorf("missing %q in:\n%s", want, out.String())
		}
	}
}

func TestCheck_mountedAgentsFileIsComparedInItsFolder(t *testing.T) {
	agents, repo := mountFixture(t)
	write(t, repo, ".claude/agents/coding-engineer.md", "engineer\n", 0o644)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, Mounts: []Mount{{Name: "kagi", Root: "projects/kagi"}}})
	if err != nil {
		t.Fatal(err)
	}
	// The server composes the mounted pack's AGENTS.md into its folder.
	release := client.Release{Number: 1, RepositoryID: "repo-1"}
	for _, set := range plan.Request.Sets {
		for _, file := range set.Files {
			path := file.Path
			if set.Name == "kagi" && path == "AGENTS.md" {
				path = "projects/kagi/AGENTS.md"
			}
			release.Files = append(release.Files, client.ReleaseEntry{Path: path, SHA256: file.SHA256, Mode: file.Mode, Target: file.SymlinkTarget})
		}
	}
	fake := &fakeKaya{blobs: map[string]bool{}, release: release}
	server := httptest.NewServer(fake.handler(t))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
	run, _, err := Send(context.Background(), api, plan)
	if err != nil {
		t.Fatal(err)
	}
	checks, err := Check(context.Background(), api, plan, run)
	if err != nil {
		t.Fatal(err)
	}
	if len(checks) != 1 || len(checks[0].Mismatches) != 0 || checks[0].Skipped != "" || !Passed(run, checks) {
		t.Fatalf("checks = %+v, want the release equal to the disk", checks)
	}
}
