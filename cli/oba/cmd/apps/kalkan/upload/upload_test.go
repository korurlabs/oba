package upload

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

var docsLayout = map[string]string{
	"docs/index.md":         "# Docs home\n[g](guide.md)\n",
	"docs/guide.md":         "# Guide\nbody\n",
	"docs/guide.tr.md":      "# Kilavuz\ngovde\n",
	"docs/sub/deep.md":      "# Deep\n",
	"docs/handoffs/skip.md": "# Skip\n",
	"docs/.hidden/skip2.md": "# Skip\n",
}

// setup signs in, writes files into a new working directory and returns the fake and that
// directory.
func setup(t *testing.T, files map[string]string) (*fakeKalkan, string) {
	t.Helper()
	f, work := newFakeKalkan(t)
	writeTree(t, work, files)
	return f, work
}

func seedDocs(f *fakeKalkan) {
	f.seed("eng", 1, en("E", ""))
	f.seed("eng/docs", 1, en("Docs home", "[g](/eng/docs/guide)\n"))
	f.seed("eng/docs/guide", 1, en("Guide", "body\n"), tr("Kilavuz", "govde\n"))
	f.seed("eng/docs/sub", 1, en("sub", ""))
	f.seed("eng/docs/sub/deep", 1, en("Deep", ""))
}

func TestUploadCreatesThenSecondRunIsAllUnchanged(t *testing.T) {
	f, _ := setup(t, docsLayout)
	f.seed("eng", 1, en("Engineering", ""))

	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 0 || r.Stderr != "" {
		t.Fatalf("exit %d stderr %q err %v", r.Exit, r.Stderr, r.Err)
	}
	if out.Counts["created"] != 4 || len(out.Pages) != 4 {
		t.Fatalf("report %+v", out)
	}
	for _, key := range []string{"created", "updated", "unchanged", "conflict", "denied", "failed", "skipped"} {
		if _, ok := out.Counts[key]; !ok {
			t.Errorf("counts lack %q: scripts read every key", key)
		}
	}
	for _, path := range []string{"eng/docs", "eng/docs/guide", "eng/docs/sub", "eng/docs/sub/deep"} {
		if out.outcome(t, path) != "created" {
			t.Errorf("%s = %s", path, out.outcome(t, path))
		}
	}
	if f.page("eng/docs/handoffs") != nil || f.page("eng/docs/.hidden") != nil {
		t.Fatal("handoffs and hidden directories must not be uploaded")
	}
	guide := f.page("eng/docs/guide")
	if guide.Translations["EN"].Title != "Guide" || guide.Translations["TR"].Title != "Kilavuz" {
		t.Fatalf("guide = %+v", guide.Translations)
	}
	if body := f.body("eng/docs", "EN"); body != "[g](/eng/docs/guide)\n" {
		t.Fatalf("link rewrite: body = %q", body)
	}

	writesBefore := f.count("PUT", upsertRoute)
	out, r = runJSON(t, "docs", "eng/docs")
	if r.Exit != 0 || out.Counts["unchanged"] != 4 || out.Counts["created"] != 0 || out.Counts["updated"] != 0 {
		t.Fatalf("second run exit %d report %+v", r.Exit, out)
	}
	for _, page := range out.Pages {
		if page.Version != 1 {
			t.Errorf("%s version %d: an unchanged upload must not move the version", page.Path, page.Version)
		}
	}
	if f.page("eng/docs").Version != 1 || f.count("PUT", upsertRoute) != writesBefore+3 {
		t.Fatal("second run: one upsert per page with an index file (not the directory page), no version change")
	}
	if f.count("POST", "/") != 0 {
		t.Fatal("no file to upload, no upload")
	}
}

func TestUploadEveryCallActsInTheChosenOrganization(t *testing.T) {
	f, _ := setup(t, map[string]string{"d/a.md": "# A\n"})
	f.seed("r", 1, en("R", ""))
	if r := run(t, "d", "r"); r.Exit != 0 {
		t.Fatalf("exit %d err %v", r.Exit, r.Err)
	}
	for _, req := range f.api.Requests() {
		if req.Path != "/v3/api-docs" && req.Org != clitest.OrgIDAcme {
			t.Fatalf("%s %s acted in %q", req.Method, req.Path, req.Org)
		}
	}
}

func TestUploadDefaultLanguageFlagAndFileLanguage(t *testing.T) {
	f, _ := setup(t, map[string]string{"d/a.md": "# A\n"})
	f.seed("r", 1, en("R", ""))
	if r := run(t, "d", "r", "--lang", "tr"); r.Exit != 0 {
		t.Fatalf("exit %d err %v", r.Exit, r.Err)
	}
	if _, ok := f.page("r/a").Translations["TR"]; !ok {
		t.Fatalf("translations = %+v, want TR", f.page("r/a").Translations)
	}
	if r := run(t, "d", "r", "--lang", "xx"); r.Exit != 1 || r.Err == nil || !strings.Contains(r.Err.Error(), "use tr or en") {
		t.Fatalf("exit %d err %v", r.Exit, r.Err)
	}
}

func TestUploadFromKalkanYAMLInSubdirectoryUsesItsOrganization(t *testing.T) {
	f, work := setup(t, map[string]string{
		"kalkan.yaml": "organization: " + clitest.OrgIDBeta + "\nupload:\n  - dir: docs\n    to: eng/docs\n  - dir: plans\n    to: eng/plans\n    lang: tr\n",
		"docs/a.md":   "# A\n",
		"plans/b.md":  "# B\n",
		"deep/x/keep": "",
	})
	if err := os.Mkdir(filepath.Join(work, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	f.seed("eng", 1, en("E", ""))
	t.Chdir(filepath.Join(work, "deep", "x"))
	out, r := runJSON(t)
	if r.Exit != 0 || out.Counts["created"] != 4 {
		t.Fatalf("exit %d stderr %q report %+v", r.Exit, r.Stderr, out)
	}
	for _, req := range f.api.Requests() {
		if req.Path != "/v3/api-docs" && req.Org != clitest.OrgIDBeta {
			t.Fatalf("%s %s acted in %q, want the organization of kalkan.yaml", req.Method, req.Path, req.Org)
		}
	}
	if _, ok := f.page("eng/plans/b").Translations["TR"]; !ok {
		t.Fatal("the entry's lang: tr must set the locale of that directory")
	}
	if _, ok := f.page("eng/docs/a").Translations["EN"]; !ok {
		t.Fatal("an entry without lang uses the --lang default")
	}
	if f.count("GET", pagesRoute) < 2 {
		t.Fatal("every bundle reads the tree")
	}
}

func TestUploadKalkanYAMLOrganizationByName(t *testing.T) {
	f, _ := setup(t, map[string]string{
		"kalkan.yaml": "organization: Docs Org\nupload:\n  - dir: d\n    to: r\n",
		"d/a.md":      "# A\n",
	})
	f.api.Respond("GET /users/me", http.StatusOK, `{"data":{"id":"u1","email":"a@b.c","organizations":[`+
		`{"id":"`+clitest.OrgIDAcme+`","name":"Acme","role":"ADMIN","organizationStatus":"ACTIVE"},`+
		`{"id":"`+clitest.OrgIDBeta+`","name":"Docs Org","role":"MEMBER","organizationStatus":"ACTIVE"}]}}`)
	f.seed("r", 1, en("R", ""))
	if r := run(t); r.Exit != 0 {
		t.Fatalf("exit %d stderr %q err %v", r.Exit, r.Stderr, r.Err)
	}
	if got := f.api.Request("PUT " + upsertRoute).Org; got != clitest.OrgIDBeta {
		t.Fatalf("upsert acted in %q, want the organization kalkan.yaml names", got)
	}
}

func TestUploadOrgFlagWinsOverKalkanYAML(t *testing.T) {
	f, _ := setup(t, map[string]string{
		"kalkan.yaml": "organization: " + clitest.OrgIDBeta + "\nupload:\n  - dir: d\n    to: r\n",
		"d/a.md":      "# A\n",
	})
	f.seed("r", 1, en("R", ""))
	// The oba root sets the override from --org before every run.
	session.UseOrganization(clitest.OrgIDBeta2)
	if r := run(t); r.Exit != 0 {
		t.Fatalf("exit %d err %v", r.Exit, r.Err)
	}
	if got := f.api.Request("PUT " + upsertRoute).Org; got != clitest.OrgIDBeta2 {
		t.Fatalf("upsert acted in %q, want the --org one", got)
	}
}

func TestUploadLocalErrorsMakeNoCall(t *testing.T) {
	cases := map[string]struct {
		files map[string]string
		args  []string
		want  string
	}{
		"invalid slug":    {map[string]string{"d/bad name.md": "x"}, []string{"d", "r"}, "not a valid slug"},
		"bad target":      {map[string]string{"d/a.md": "x"}, []string{"d", "r/ü"}, "not a valid slug"},
		"too deep":        {map[string]string{"d/a.md": "x"}, []string{"d", "a/b/c/d/e/f/g/h/i/j"}, "deeper than 10"},
		"missing dir":     {nil, []string{"nope", "r"}, "cannot be read"},
		"no markdown":     {map[string]string{"d/x.txt": "x"}, []string{"d", "r"}, "no Markdown"},
		"front conflict":  {map[string]string{"d/a.md": "---\nposition: 1\n---\n", "d/a.tr.md": "---\nposition: 2\n---\n"}, []string{"d", "r"}, "must agree"},
		"one argument":    {nil, []string{"d"}, "name both"},
		"no kalkan.yaml":  {nil, nil, "no kalkan.yaml in this directory or at the git root; run oba kalkan upload <dir> <target>"},
		"no entries":      {map[string]string{"kalkan.yaml": "organization: o\n"}, nil, "lists no upload entries"},
		"bad yaml":        {map[string]string{"kalkan.yaml": "upload: [\n"}, nil, "kalkan.yaml"},
		"push key":        {map[string]string{"kalkan.yaml": "push:\n  - dir: d\n    to: r\n", "d/a.md": "x"}, nil, "push"},
		"yaml bad target": {map[string]string{"kalkan.yaml": "upload:\n  - dir: d\n    to: bad slug\n", "d/a.md": "x"}, nil, "not a valid slug"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			f, _ := setup(t, tc.files)
			r := run(t, tc.args...)
			if r.Exit != 1 || r.Err == nil || !strings.Contains(r.Err.Error(), tc.want) || len(f.api.Calls()) != 0 {
				t.Fatalf("exit %d err %v calls %v, want 1 containing %q and no call", r.Exit, r.Err, f.api.Calls(), tc.want)
			}
			if r.Stdout != "" || r.Stderr != "" {
				t.Fatalf("stdout %q stderr %q: the root prints the error once", r.Stdout, r.Stderr)
			}
		})
	}
}

func TestUploadLocalErrorKeepsAnEmptyJSONReport(t *testing.T) {
	setup(t, map[string]string{"d/bad name.md": "x"})
	out, r := runJSON(t, "d", "r")
	if r.Exit != 1 || len(out.Pages) != 0 || out.Counts["created"] != 0 || len(out.Counts) != 7 {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
}

func TestUploadMissingTargetParentFailsAndSkipsChildren(t *testing.T) {
	f, _ := setup(t, docsLayout)
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 1 {
		t.Fatalf("exit %d", r.Exit)
	}
	if out.outcome(t, "eng/docs") != "failed" || out.Counts["failed"] != 1 || out.Counts["skipped"] != 3 {
		t.Fatalf("report %+v", out)
	}
	for _, page := range out.Pages {
		if page.Path == "eng/docs" {
			if page.Error == nil || page.Error.Code != "KLK_VAL_001" {
				t.Fatalf("page %+v", page)
			}
			continue
		}
		if page.Outcome != "skipped" || page.Error == nil || page.Error.Code != "PARENT_NOT_WRITTEN" ||
			page.Error.Message != "not uploaded: eng/docs above it was not written" {
			t.Errorf("page %+v", page)
		}
	}
	if f.page("eng/docs") != nil || f.page("eng/docs/guide") != nil {
		t.Fatal("nothing may be written below a page that failed")
	}
}

func TestUploadConflictSkipsThatPageWritesTheRestAndForceOverwrites(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	f.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n", "docs/sub/deep.md": "# Deep\nchanged\n"})

	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 4 {
		t.Fatalf("exit %d, want 4", r.Exit)
	}
	if out.outcome(t, "eng/docs/guide") != "conflict" || out.outcome(t, "eng/docs/sub/deep") != "updated" || out.outcome(t, "eng/docs") != "unchanged" {
		t.Fatalf("report %+v", out)
	}
	if out.Counts["conflict"] != 1 || out.Counts["updated"] != 1 {
		t.Fatalf("counts %+v", out.Counts)
	}
	if f.body("eng/docs/guide", "EN") != "body\n" {
		t.Fatal("a conflict must leave the page unchanged")
	}
	for _, page := range out.Pages {
		if page.Outcome == "conflict" && !strings.Contains(page.Error.Message, "Last edited by Ada at 2026-01-02T03:04:05Z. Pass --force to overwrite it.") {
			t.Fatalf("message %q", page.Error.Message)
		}
	}

	out, r = runJSON(t, "docs", "eng/docs", "--force")
	if r.Exit != 0 || out.outcome(t, "eng/docs/guide") != "updated" || f.body("eng/docs/guide", "EN") != "changed\n" {
		t.Fatalf("force: exit %d report %+v", r.Exit, out)
	}
	calls := f.upsertCalls()
	if !calls[len(calls)-1].In.Force {
		t.Fatal("--force must be sent")
	}
}

// Exit status: 0, else the first of 1 (a failure), 3 (a 403), 2 (a 404) and 4 (a conflict).
func TestUploadExitStatusPrecedence(t *testing.T) {
	cases := []struct {
		name    string
		webEdit bool
		status  map[string]int
		want    int
	}{
		{"conflict only", true, nil, 4},
		{"conflict and hidden", true, map[string]int{"eng/docs/sub/deep": 404}, 2},
		{"conflict and denied", true, map[string]int{"eng/docs/sub/deep": 403}, 3},
		{"hidden and denied", false, map[string]int{"eng/docs/sub/deep": 404, "eng/docs/guide": 403}, 3},
		{"conflict and failed", true, map[string]int{"eng/docs/sub/deep": 500}, 1},
		{"denied and failed", false, map[string]int{"eng/docs/sub/deep": 403, "eng/docs/guide": 500}, 1},
		{"conflict and unauthorized", true, map[string]int{"eng/docs/sub/deep": 401}, 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			f, work := setup(t, docsLayout)
			seedDocs(f)
			writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n", "docs/sub/deep.md": "# Deep\nchanged\n"})
			if tc.webEdit {
				f.webEdited["eng/docs/guide"] = true
			}
			for path, status := range tc.status {
				f.status[path] = status
			}
			if _, r := runJSON(t, "docs", "eng/docs"); r.Exit != tc.want {
				t.Fatalf("exit %d, want %d; stderr %q", r.Exit, tc.want, r.Stderr)
			}
		})
	}
}

func TestUploadDeniedPageSkipsItsSubtreeOnly(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	writeTree(t, work, map[string]string{"docs/sub/index.md": "# Sub\nnew\n"})
	f.status["eng/docs/sub"] = http.StatusForbidden
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 3 {
		t.Fatalf("exit %d", r.Exit)
	}
	if out.outcome(t, "eng/docs/sub") != "denied" || out.outcome(t, "eng/docs/sub/deep") != "skipped" || out.outcome(t, "eng/docs/guide") != "unchanged" {
		t.Fatalf("report %+v", out)
	}
}

// A token without a refresh, as OBA_TOKEN is, reaches the run as the API's 401.
func TestUploadRefusedSignInStopsTheRun(t *testing.T) {
	f, work := setup(t, docsLayout)
	t.Setenv(config.EnvToken, "env-token")
	t.Setenv(config.EnvAPIURL, f.api.URL())
	session.UseOrganization(clitest.OrgIDAcme)
	seedDocs(f)
	f.status["eng/docs/guide"] = http.StatusUnauthorized
	// Later pages change, so they would be written if the run went on.
	writeTree(t, work, map[string]string{"docs/sub/deep.md": "# Deep\nchanged\n"})
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 1 {
		t.Fatalf("exit %d", r.Exit)
	}
	if out.outcome(t, "eng/docs/guide") != "failed" || out.outcome(t, "eng/docs/sub") != "skipped" || out.outcome(t, "eng/docs/sub/deep") != "skipped" {
		t.Fatalf("report %+v", out)
	}
	if f.body("eng/docs/sub/deep", "EN") != "" {
		t.Fatal("no page may be written after the sign-in was refused")
	}
	if len(out.Pages) != 4 {
		t.Fatalf("every page gets an outcome, got %d", len(out.Pages))
	}
}

func TestUploadTreeFailureIsRunLevelAndSkipsBundle(t *testing.T) {
	f, _ := setup(t, docsLayout)
	f.listFails = http.StatusInternalServerError
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 1 || out.Counts["skipped"] != 4 || strings.Count(r.Stderr, "\n") != 1 || f.count("PUT", pagesRoute) != 0 {
		t.Fatalf("exit %d report %+v stderr %q", r.Exit, out.Counts, r.Stderr)
	}
	f.listFails = http.StatusForbidden
	if out, r = runJSON(t, "docs", "eng/docs"); r.Exit != 3 || out.Counts["skipped"] != 4 {
		t.Fatalf("exit %d counts %+v", r.Exit, out.Counts)
	}
}

func TestUploadWithoutSessionSkipsEverything(t *testing.T) {
	clitest.SetupHome(t)
	t.Chdir(t.TempDir())
	writeTree(t, ".", docsLayout)
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 1 || out.Counts["skipped"] != 4 || strings.Count(r.Stderr, "\n") != 1 || !strings.HasPrefix(r.Stderr, "Error: ") {
		t.Fatalf("exit %d counts %+v stderr %q", r.Exit, out.Counts, r.Stderr)
	}
}

func TestUploadDryRunWritesAndUploadsNothing(t *testing.T) {
	f, _ := setup(t, map[string]string{
		"docs/index.md": "# Home\n![l](logo.png)\n",
		"docs/new/a.md": "# A\n![l](logo.png)\n",
		"docs/new/b.md": "# B\n",
		"docs/logo.png": "PNG",
	})
	f.seed("eng", 1, en("E", ""))
	r := run(t, "docs", "eng/docs", "--dry-run")
	if r.Exit != 0 {
		t.Fatalf("exit %d stderr %q err %v", r.Exit, r.Stderr, r.Err)
	}
	if !strings.Contains(r.Stdout, "created   eng/docs/new/b (version 0)") || !strings.HasSuffix(r.Stdout, "4 pages: 4 created (dry run, nothing written)\n") {
		t.Fatalf("stdout:\n%s", r.Stdout)
	}
	if f.page("eng/docs") != nil || f.uploadCount() != 0 || f.count("POST", "/") != 0 {
		t.Fatal("a dry run writes nothing")
	}
	calls := f.upsertCalls()
	for _, c := range calls {
		if c.Query != "dryRun=true" {
			t.Fatalf("query %q: every upsert of a dry run carries dryRun=true", c.Query)
		}
	}
	// Below a page the run would create, the server cannot be asked: one call for eng/docs only.
	if len(calls) != 1 {
		t.Fatalf("%d upserts, want 1 (pages below a page to create are created without a call)", len(calls))
	}
}

func TestUploadDryRunReportsUpdatesAndConflicts(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	f.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n", "docs/sub/deep.md": "# Deep\nchanged\n"})
	out, r := runJSON(t, "docs", "eng/docs", "--dry-run")
	if r.Exit != 4 || out.outcome(t, "eng/docs/guide") != "conflict" || out.outcome(t, "eng/docs/sub/deep") != "updated" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	if f.body("eng/docs/sub/deep", "EN") != "" || f.page("eng/docs/sub/deep").Version != 1 {
		t.Fatal("a dry run changed a page")
	}
}

func TestUploadDirectoryPageWithoutIndexIsLeftAlone(t *testing.T) {
	f, _ := setup(t, map[string]string{"d/sub/a.md": "# A\n"})
	f.seed("r", 1, en("R", ""))
	f.seed("r/sub", 4, en("Hand written", "people wrote this"))
	f.webEdited["r/sub"] = true
	out, r := runJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/sub") != "unchanged" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	for _, page := range out.Pages {
		if page.Path == "r/sub" && page.Version != 4 {
			t.Fatalf("version %d, want the stored 4", page.Version)
		}
	}
	if f.body("r/sub", "EN") != "people wrote this" {
		t.Fatal("a directory without index file must not blank its page")
	}
	for _, c := range f.upsertCalls() {
		if c.In.Path == "r/sub" {
			t.Fatal("no upsert may be sent for an existing directory page")
		}
	}
	f.getFails = http.StatusInternalServerError
	if _, r := runJSON(t, "d", "r"); r.Exit != 1 {
		t.Fatalf("a failing read of the directory page: exit %d", r.Exit)
	}
}

func TestUploadDirectoryPageWithoutIndexIsCreatedWhenMissing(t *testing.T) {
	f, _ := setup(t, map[string]string{"d/sub/a.md": "# A\n"})
	f.seed("r", 1, en("R", ""))
	out, r := runJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/sub") != "created" || f.page("r/sub").Translations["EN"].Title != "sub" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
}

func assetLayout() map[string]string {
	return map[string]string{
		"d/a.md":         "# A\n![logo](img/logo.png)\n",
		"d/b.md":         "# B\n![logo](img/logo.png) [spec](img/spec.pdf)\n",
		"d/img/logo.png": "LOGO",
		"d/img/spec.pdf": "SPEC",
	}
}

func TestUploadAssetsPerPageAndRewritesLinks(t *testing.T) {
	f, _ := setup(t, assetLayout())
	f.seed("r", 1, en("R", ""))
	out, r := runJSON(t, "d", "r")
	if r.Exit != 0 || out.Counts["created"] != 2 || out.outcome(t, "r") != "unchanged" {
		t.Fatalf("exit %d stderr %q report %+v", r.Exit, r.Stderr, out)
	}
	if f.uploadCount() != 3 {
		t.Fatalf("uploads = %+v, want the logo for each page and the spec once", f.uploads)
	}
	for _, u := range f.uploads {
		if len(u.FileName) < 13 || u.FileName[12] != '-' {
			t.Fatalf("upload name %q must be <12 hex>-<name>", u.FileName)
		}
	}
	a := f.body("r/a", "EN")
	b := f.body("r/b", "EN")
	if !strings.Contains(a, "![logo](http") || strings.Contains(a, "sig=") || strings.Contains(a, "img/logo.png") {
		t.Fatalf("a body = %q: the link is the unsigned URL", a)
	}
	aLogo := a[strings.Index(a, "(")+1 : strings.Index(a, ")")]
	bLogo := b[strings.Index(b, "(")+1 : strings.Index(b, ")")]
	if !strings.Contains(aLogo, "/"+f.page("r/a").ID+"/") || !strings.Contains(bLogo, "/"+f.page("r/b").ID+"/") || !strings.Contains(b, "[spec](http") {
		t.Fatalf("a logo %q, b body %q: every page links its own copy", aLogo, b)
	}

	uploadsBefore := f.uploadCount()
	out, r = runJSON(t, "d", "r")
	if r.Exit != 0 || out.Counts["unchanged"] != 3 || f.uploadCount() != uploadsBefore {
		t.Fatalf("second run: exit %d counts %+v uploads %d", r.Exit, out.Counts, f.uploadCount())
	}
}

func TestUploadChangedAssetGetsANewNameAndUpload(t *testing.T) {
	f, work := setup(t, assetLayout())
	f.seed("r", 1, en("R", ""))
	if r := run(t, "d", "r"); r.Exit != 0 {
		t.Fatalf("exit %d err %v", r.Exit, r.Err)
	}
	first := f.uploadCount()
	writeTree(t, work, map[string]string{"d/img/logo.png": "LOGO v2"})
	out, r := runJSON(t, "d", "r")
	if r.Exit != 0 || f.uploadCount() != first+2 || out.outcome(t, "r/a") != "updated" || out.outcome(t, "r/b") != "updated" {
		t.Fatalf("exit %d uploads %d report %+v", r.Exit, f.uploadCount(), out)
	}
}

func TestUploadSameFileTwiceOnOnePageUploadsOnce(t *testing.T) {
	f, _ := setup(t, map[string]string{
		"d/a.md":     "# A\n![one](l.png) ![two](./l.png) ![copy](copy.png)\n",
		"d/l.png":    "LOGO",
		"d/copy.png": "LOGO",
	})
	f.seed("r", 1, en("R", ""))
	out, r := runJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/a") != "created" {
		t.Fatalf("exit %d stderr %q report %+v", r.Exit, r.Stderr, out)
	}
	if f.uploadCount() != 1 {
		t.Fatalf("uploads = %+v, want one copy for the page", f.uploads)
	}
	link := f.api.URL() + "/files/" + f.page("r/a").ID + "/"
	if body := f.body("r/a", "EN"); strings.Count(body, "]("+link) != 3 {
		t.Fatalf("body = %q: every link points at the page's one copy", body)
	}
}

func TestUploadAssetProblemsFailOnlyThatPage(t *testing.T) {
	f, _ := setup(t, map[string]string{"d/ok.md": "# Ok\n", "d/bad.md": "# Bad\n![x](zero.png)\n", "d/zero.png": ""})
	f.seed("r", 1, en("R", ""))
	out, r := runJSON(t, "d", "r")
	if r.Exit != 1 || out.outcome(t, "r/bad") != "failed" || out.outcome(t, "r/ok") != "created" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	if f.page("r/bad") != nil {
		t.Fatal("a page with an unusable asset must not be written")
	}
}

func TestUploadDuplicateContentFailsWithHint(t *testing.T) {
	f, _ := setup(t, map[string]string{"d/a.md": "# A\n![x](l.png)\n", "d/l.png": "LOGO"})
	f.seed("r", 1, en("R", ""))
	// The API refuses the bytes as a file the page already holds.
	f.hashes[sha256Hex("LOGO")] = true
	out, r := runJSON(t, "d", "r")
	if r.Exit != 1 || out.outcome(t, "r/a") != "failed" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	for _, page := range out.Pages {
		if page.Path != "r/a" {
			continue
		}
		for _, want := range []string{"upload ", "l.png: ", "this Kalkan page already holds a file with the same content", "duplicate content"} {
			if page.Error.Code != client.CodeDuplicateContent || !strings.Contains(page.Error.Message, want) {
				t.Fatalf("error %+v lacks %q", page.Error, want)
			}
		}
	}
}

func TestUploadExistingPageWithAssetsListsAttachmentsFirst(t *testing.T) {
	f, _ := setup(t, assetLayout())
	f.seed("r", 1, en("R", ""))
	if r := run(t, "d", "r"); r.Exit != 0 {
		t.Fatalf("exit %d err %v", r.Exit, r.Err)
	}
	// A web edit changed the body; the assets are still attached and are not uploaded again.
	f.page("r/a").Translations["EN"] = en("A", "different")
	uploads := f.uploadCount()
	listsBefore := f.count("GET", attachmentsRoute)
	out, r := runJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/a") != "updated" || f.uploadCount() != uploads {
		t.Fatalf("exit %d uploads %d report %+v", r.Exit, f.uploadCount(), out)
	}
	if f.count("GET", attachmentsRoute) == listsBefore {
		t.Fatal("attachments of an existing page must be listed")
	}
}

func TestUploadJSONPrintsEachPageErrorOnceOnStderr(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	f.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n"})
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 4 || out.outcome(t, "eng/docs/guide") != "conflict" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	if r.Stdout != "" {
		t.Fatalf("stdout %q: with --json the report lines are not printed", r.Stdout)
	}
	if strings.Count(r.Stderr, "\n") != 1 || !strings.HasPrefix(r.Stderr, "Error: eng/docs/guide: The page was edited in Kalkan.") ||
		!strings.HasSuffix(r.Stderr, "(KLK_STA_004)\n") {
		t.Fatalf("stderr %q: the error of a page prints once", r.Stderr)
	}
}

func TestUploadTextOutput(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	f.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n"})
	r := run(t, "docs", "eng/docs")
	if r.Exit != 4 {
		t.Fatalf("exit %d", r.Exit)
	}
	for _, want := range []string{"unchanged eng/docs (version 1)", "conflict  eng/docs/guide\n", "4 pages: 3 unchanged, 1 conflict\n"} {
		if !strings.Contains(r.Stdout, want) {
			t.Errorf("stdout lacks %q:\n%s", want, r.Stdout)
		}
	}
	if strings.Count(r.Stderr, "\n") != 1 || !strings.HasPrefix(r.Stderr, "Error: eng/docs/guide: ") || !strings.Contains(r.Stderr, "(KLK_STA_004)") {
		t.Fatalf("stderr %q: the error of a page prints once", r.Stderr)
	}
}

func TestUploadTextPrintsARunLevelErrorOnce(t *testing.T) {
	f, _ := setup(t, docsLayout)
	f.listFails = http.StatusInternalServerError
	r := run(t, "docs", "eng/docs")
	if r.Exit != 1 || !strings.Contains(r.Stdout, "4 pages: 4 skipped") {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	if strings.Count(r.Stderr, "\n") != 1 || !strings.HasSuffix(r.Stderr, "(HTTP_500)\n") {
		t.Fatalf("stderr %q: the run-level error prints once, the skipped pages add nothing", r.Stderr)
	}
	for _, path := range []string{"eng/docs", "eng/docs/guide", "eng/docs/sub", "eng/docs/sub/deep"} {
		if !strings.Contains(r.Stdout, "skipped   "+path+": not uploaded: list failed (HTTP_500)\n") {
			t.Errorf("stdout lacks the cause of %s:\n%s", path, r.Stdout)
		}
	}
}

func TestUploadTextPrintsAParentErrorOnceForItsSkippedSubtree(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	writeTree(t, work, map[string]string{"docs/sub/index.md": "# Sub\nnew\n"})
	f.status["eng/docs/sub"] = http.StatusForbidden
	r := run(t, "docs", "eng/docs")
	want := "skipped   eng/docs/sub/deep: not uploaded: eng/docs/sub above it was not written (PARENT_NOT_WRITTEN)\n"
	if r.Exit != 3 || !strings.Contains(r.Stdout, want) {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	if strings.Count(r.Stderr, "\n") != 1 || !strings.HasPrefix(r.Stderr, "Error: eng/docs/sub: ") {
		t.Fatalf("stderr %q: only the denied page's error prints", r.Stderr)
	}
}

func TestUploadJSONErrorShape(t *testing.T) {
	f, work := setup(t, docsLayout)
	seedDocs(f)
	f.status["eng/docs/guide"] = http.StatusForbidden
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n"})
	out, r := runJSON(t, "docs", "eng/docs")
	if r.Exit != 3 {
		t.Fatalf("exit %d", r.Exit)
	}
	for _, page := range out.Pages {
		switch page.Outcome {
		case "created", "updated", "unchanged":
			if page.Error != nil {
				t.Errorf("%s has an error on a success outcome", page.Path)
			}
		default:
			if page.Error == nil || page.Error.Code == "" {
				t.Errorf("%s has no error", page.Path)
			}
		}
	}
}
