package cmd

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// pushSetup signs in, makes a repository directory with a kalkan.yaml and returns the work dir.
func pushSetup(t *testing.T, files map[string]string) (*fakeAPI, string) {
	t.Helper()
	work := isolate(t)
	api := newFakeAPI(t)
	signIn(t, api)
	writeTree(t, work, files)
	return api, work
}

type pushOut struct {
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

func (p pushOut) outcome(t *testing.T, path string) string {
	t.Helper()
	for _, page := range p.Pages {
		if page.Path == path {
			return page.Outcome
		}
	}
	t.Fatalf("no page %s in report", path)
	return ""
}

func pushJSON(t *testing.T, args ...string) (pushOut, result) {
	t.Helper()
	r := run(t, append([]string{"push", "--json"}, args...)...)
	var out pushOut
	decodeJSON(t, r.Stdout, &out)
	return out, r
}

var docsLayout = map[string]string{
	"docs/index.md":         "# Docs home\n[g](guide.md)\n",
	"docs/guide.md":         "# Guide\nbody\n",
	"docs/guide.tr.md":      "# Kilavuz\ngovde\n",
	"docs/sub/deep.md":      "# Deep\n",
	"docs/handoffs/skip.md": "# Skip\n",
	"docs/.hidden/skip2.md": "# Skip\n",
}

func TestPushCreatesThenSecondRunIsAllUnchanged(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	api.seed("eng", 1, en("Engineering", ""))

	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != 0 || r.Stderr != "" {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
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
	if api.pages["eng/docs/handoffs"] != nil || api.pages["eng/docs/.hidden"] != nil {
		t.Fatal("handoffs and hidden directories must not be pushed")
	}
	guide := api.pages["eng/docs/guide"]
	if guide.Translations["EN"].Title != "Guide" || guide.Translations["TR"].Title != "Kilavuz" {
		t.Fatalf("guide = %+v", guide.Translations)
	}
	if body := api.pages["eng/docs"].Translations["EN"].Body; body != "[g](/eng/docs/guide)\n" {
		t.Fatalf("link rewrite: body = %q", body)
	}

	writesBefore := api.count("PUT", "/kalkan/pages/by-path")
	out, r = pushJSON(t, "docs", "eng/docs")
	if r.Exit != 0 || out.Counts["unchanged"] != 4 || out.Counts["created"] != 0 || out.Counts["updated"] != 0 {
		t.Fatalf("second run exit %d report %+v", r.Exit, out)
	}
	for _, page := range out.Pages {
		if page.Version != 1 {
			t.Errorf("%s version %d: an unchanged push must not move the version", page.Path, page.Version)
		}
	}
	if api.pages["eng/docs"].Version != 1 || api.count("PUT", "/kalkan/pages/by-path") != writesBefore+3 {
		t.Fatal("second run: one upsert per page with an index file (not the directory page), no version change")
	}
}

func TestPushDefaultLanguageFlagAndFileLanguage(t *testing.T) {
	api, _ := pushSetup(t, map[string]string{"d/a.md": "# A\n"})
	api.seed("r", 1, en("R", ""))
	if r := run(t, "push", "d", "r", "--lang", "tr"); r.Exit != 0 {
		t.Fatalf("exit %d %s", r.Exit, r.Stderr)
	}
	if _, ok := api.pages["r/a"].Translations["TR"]; !ok {
		t.Fatalf("translations = %+v, want TR", api.pages["r/a"].Translations)
	}
	if r := run(t, "push", "d", "r", "--lang", "xx"); r.Exit != ExitFailure || !strings.Contains(r.Stderr, "use tr or en") {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
}

func TestPushFromKalkanYAMLInSubdirectoryUsesItsOrganization(t *testing.T) {
	api, work := pushSetup(t, map[string]string{
		"kalkan.yaml": "organization: org-yaml\npush:\n  - dir: docs\n    to: eng/docs\n  - dir: plans\n    to: eng/plans\n    lang: tr\n",
		"docs/a.md":   "# A\n",
		"plans/b.md":  "# B\n",
		"deep/x/keep": "",
	})
	if err := os.Mkdir(filepath.Join(work, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	api.seed("eng", 1, en("E", ""))
	t.Chdir(filepath.Join(work, "deep", "x"))
	out, r := pushJSON(t)
	if r.Exit != 0 || out.Counts["created"] != 4 {
		t.Fatalf("exit %d stderr %q report %+v", r.Exit, r.Stderr, out)
	}
	for _, c := range api.calls {
		if c.Org != "org-yaml" {
			t.Fatalf("a call acted in %q, want org-yaml", c.Org)
		}
	}
	if _, ok := api.pages["eng/plans/b"].Translations["TR"]; !ok {
		t.Fatal("the entry's lang: tr must set the locale of that directory")
	}
	if _, ok := api.pages["eng/docs/a"].Translations["EN"]; !ok {
		t.Fatal("an entry without lang uses the --lang default")
	}
	if api.count("GET", "/kalkan/pages") < 2 {
		t.Fatal("every bundle reads the tree")
	}
}

func TestPushLocalErrorsMakeNoCall(t *testing.T) {
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
		"no kalkan.yaml":  {nil, nil, "no kalkan.yaml"},
		"no entries":      {map[string]string{"kalkan.yaml": "organization: o\n"}, nil, "lists no push entries"},
		"bad yaml":        {map[string]string{"kalkan.yaml": "push: [\n"}, nil, "kalkan.yaml"},
		"yaml bad target": {map[string]string{"kalkan.yaml": "push:\n  - dir: d\n    to: bad slug\n", "d/a.md": "x"}, nil, "not a valid slug"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			api, _ := pushSetup(t, tc.files)
			r := run(t, append([]string{"push", "--json"}, tc.args...)...)
			if r.Exit != ExitFailure || !strings.Contains(r.Stderr, tc.want) || api.total() != 0 {
				t.Fatalf("exit %d stderr %q calls %d, want 1 containing %q and no call", r.Exit, r.Stderr, api.total(), tc.want)
			}
			if strings.Count(r.Stderr, "\n") != 1 {
				t.Fatalf("the error must print once, got %q", r.Stderr)
			}
		})
	}
}

func TestPushLocalErrorPrintsEmptyReportAndJSONError(t *testing.T) {
	_, _ = pushSetup(t, map[string]string{"d/bad name.md": "x"})
	r := run(t, "push", "d", "r", "--json")
	var out pushOut
	decodeJSON(t, r.Stdout, &out)
	if r.Exit != ExitFailure || len(out.Pages) != 0 || out.Counts["created"] != 0 {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	var e errorShape
	decodeJSON(t, r.Stderr, &e)
	if e.Error.Code != codeCLIError {
		t.Fatalf("error = %+v", e)
	}
}

func TestPushMissingTargetParentFailsAndSkipsChildren(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitFailure {
		t.Fatalf("exit %d", r.Exit)
	}
	if out.outcome(t, "eng/docs") != "failed" || out.Counts["failed"] != 1 || out.Counts["skipped"] != 3 {
		t.Fatalf("report %+v", out)
	}
	for _, page := range out.Pages {
		if page.Path != "eng/docs" {
			if page.Outcome != "skipped" || page.Error == nil || page.Error.Code != "PARENT_NOT_WRITTEN" || !strings.Contains(page.Error.Message, "eng/docs") {
				t.Errorf("page %+v", page)
			}
		}
	}
	if len(api.pages) != 0 {
		t.Fatal("nothing may be written below a page that failed")
	}
	for _, page := range out.Pages {
		if page.Path == "eng/docs" && (page.Error == nil || page.Error.Code != "KLK_VAL_001") {
			t.Fatalf("page %+v", page)
		}
	}
}

func seedDocs(api *fakeAPI) {
	api.seed("eng", 1, en("E", ""))
	api.seed("eng/docs", 1, en("Docs home", "[g](/eng/docs/guide)\n"))
	api.seed("eng/docs/guide", 1, en("Guide", "body\n"), tr("Kilavuz", "govde\n"))
	api.seed("eng/docs/sub", 1, en("sub", ""))
	api.seed("eng/docs/sub/deep", 1, en("Deep", ""))
}

func TestPushConflictSkipsThatPageWritesTheRestAndForceOverwrites(t *testing.T) {
	api, work := pushSetup(t, docsLayout)
	seedDocs(api)
	api.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n", "docs/sub/deep.md": "# Deep\nchanged\n"})

	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitConflict {
		t.Fatalf("exit %d, want 3", r.Exit)
	}
	if out.outcome(t, "eng/docs/guide") != "conflict" || out.outcome(t, "eng/docs/sub/deep") != "updated" || out.outcome(t, "eng/docs") != "unchanged" {
		t.Fatalf("report %+v", out)
	}
	if out.Counts["conflict"] != 1 || out.Counts["updated"] != 1 {
		t.Fatalf("counts %+v", out.Counts)
	}
	if api.pages["eng/docs/guide"].Translations["EN"].Body != "body\n" {
		t.Fatal("a conflict must leave the page unchanged")
	}
	var conflict string
	for _, page := range out.Pages {
		if page.Outcome == "conflict" {
			conflict = page.Error.Message
		}
	}
	if !strings.Contains(conflict, "Last edited by Ada") {
		t.Fatalf("message %q", conflict)
	}

	out, r = pushJSON(t, "docs", "eng/docs", "--force")
	if r.Exit != 0 || out.outcome(t, "eng/docs/guide") != "updated" || api.pages["eng/docs/guide"].Translations["EN"].Body != "changed\n" {
		t.Fatalf("force: exit %d report %+v", r.Exit, out)
	}
	inputs, _ := api.puts()
	if !inputs[len(inputs)-1].Force {
		t.Fatal("--force must be sent")
	}
}

func TestPushExitCodePrecedence(t *testing.T) {
	// A conflict, a denial, a failure and a lost sign-in, in pairs; the first of 1, 2, 4, 3 wins.
	cases := []struct {
		name    string
		webEdit bool
		status  map[string]int
		want    int
	}{
		{"conflict only", true, nil, ExitConflict},
		{"conflict and denied", true, map[string]int{"eng/docs/sub/deep": 403}, ExitDenied},
		{"conflict and hidden", true, map[string]int{"eng/docs/sub/deep": 404}, ExitDenied},
		{"conflict and failed", true, map[string]int{"eng/docs/sub/deep": 500}, ExitFailure},
		{"denied and failed", false, map[string]int{"eng/docs/sub/deep": 403, "eng/docs/guide": 500}, ExitFailure},
		{"conflict and unauthorized", true, map[string]int{"eng/docs/sub/deep": 401}, ExitNotSignedIn},
		{"denied and unauthorized", false, map[string]int{"eng/docs/guide": 403, "eng/docs/sub/deep": 401}, ExitNotSignedIn},
		{"failed and unauthorized", false, map[string]int{"eng/docs/guide": 500, "eng/docs/sub/deep": 401}, ExitFailure},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			api, work := pushSetup(t, docsLayout)
			seedDocs(api)
			writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n", "docs/sub/deep.md": "# Deep\nchanged\n"})
			if tc.webEdit {
				api.webEdited["eng/docs/guide"] = true
			}
			for path, status := range tc.status {
				api.status[path] = status
			}
			_, r := pushJSON(t, "docs", "eng/docs")
			if r.Exit != tc.want {
				t.Fatalf("exit %d, want %d; stderr %q", r.Exit, tc.want, r.Stderr)
			}
		})
	}
}

func TestPushDeniedPageSkipsItsSubtreeOnly(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	seedDocs(api)
	api.pages["eng/docs/sub"].Translations["EN"] = en("old", "x")
	writeTree(t, filepath.Dir(filepath.Join(mustGetwd(t), "docs")), map[string]string{"docs/sub/index.md": "# Sub\nnew\n"})
	api.status["eng/docs/sub"] = 403
	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitDenied {
		t.Fatalf("exit %d", r.Exit)
	}
	if out.outcome(t, "eng/docs/sub") != "denied" || out.outcome(t, "eng/docs/sub/deep") != "skipped" || out.outcome(t, "eng/docs/guide") != "unchanged" {
		t.Fatalf("report %+v", out)
	}
}

func mustGetwd(t *testing.T) string {
	t.Helper()
	wd, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	return wd
}

func TestPushLostSignInStopsTheRun(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	seedDocs(api)
	api.status["eng/docs/guide"] = 401
	// Make later pages change so they would be written if the run went on.
	writeTree(t, mustGetwd(t), map[string]string{"docs/sub/deep.md": "# Deep\nchanged\n"})
	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitNotSignedIn {
		t.Fatalf("exit %d", r.Exit)
	}
	if out.outcome(t, "eng/docs/guide") != "failed" || out.outcome(t, "eng/docs/sub") != "skipped" || out.outcome(t, "eng/docs/sub/deep") != "skipped" {
		t.Fatalf("report %+v", out)
	}
	if api.pages["eng/docs/sub/deep"].Translations["EN"].Body != "" {
		t.Fatal("no page may be written after the sign-in was lost")
	}
	if len(out.Pages) != 4 {
		t.Fatalf("every page gets an outcome, got %d", len(out.Pages))
	}
}

func TestPushTreeFailureIsRunLevelAndSkipsBundle(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	api.listFails = 500
	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitFailure || out.Counts["skipped"] != 4 || strings.Count(r.Stderr, "\n") != 1 || api.count("PUT", "/kalkan") != 0 {
		t.Fatalf("exit %d report %+v stderr %q", r.Exit, out.Counts, r.Stderr)
	}
	api.listFails = 401
	out, r = pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitNotSignedIn || out.Counts["skipped"] != 4 {
		t.Fatalf("exit %d counts %+v", r.Exit, out.Counts)
	}
}

func TestPushWithoutSignInSkipsEverythingWithExitTwo(t *testing.T) {
	work := isolate(t)
	writeTree(t, work, docsLayout)
	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitNotSignedIn || out.Counts["skipped"] != 4 || strings.Count(r.Stderr, "\n") != 1 {
		t.Fatalf("exit %d counts %+v stderr %q", r.Exit, out.Counts, r.Stderr)
	}
}

func TestPushDryRunWritesAndUploadsNothing(t *testing.T) {
	api, _ := pushSetup(t, map[string]string{
		"docs/index.md": "# Home\n![l](logo.png)\n",
		"docs/new/a.md": "# A\n![l](logo.png)\n",
		"docs/new/b.md": "# B\n",
		"docs/logo.png": "PNG",
	})
	api.seed("eng", 1, en("E", ""))
	r := run(t, "push", "docs", "eng/docs", "--dry-run")
	if r.Exit != 0 {
		t.Fatalf("exit %d stderr %q", r.Exit, r.Stderr)
	}
	if !strings.Contains(r.Stdout, "created   eng/docs/new/b (version 0)") || !strings.HasSuffix(r.Stdout, "4 pages: 4 created (dry run, nothing written)\n") {
		t.Fatalf("stdout:\n%s", r.Stdout)
	}
	if len(api.pages) != 1 || len(api.uploads) != 0 || api.count("POST", "/") != 0 {
		t.Fatalf("pages %d uploads %d: a dry run writes nothing", len(api.pages), len(api.uploads))
	}
	_, queries := api.puts()
	for _, q := range queries {
		if q != "dryRun=true" {
			t.Fatalf("query %q: every upsert of a dry run carries dryRun=true", q)
		}
	}
	// Below a page the run would create, the server cannot be asked: one call for eng/docs only.
	if len(queries) != 1 {
		t.Fatalf("%d upserts, want 1 (pages below a page to create are created without a call)", len(queries))
	}
}

func TestPushDryRunReportsUpdatesAndConflicts(t *testing.T) {
	api, work := pushSetup(t, docsLayout)
	seedDocs(api)
	api.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n", "docs/sub/deep.md": "# Deep\nchanged\n"})
	out, r := pushJSON(t, "docs", "eng/docs", "--dry-run")
	if r.Exit != ExitConflict || out.outcome(t, "eng/docs/guide") != "conflict" || out.outcome(t, "eng/docs/sub/deep") != "updated" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	if api.pages["eng/docs/sub/deep"].Translations["EN"].Body != "" || api.pages["eng/docs/sub/deep"].Version != 1 {
		t.Fatal("a dry run changed a page")
	}
}

func TestPushDirectoryPageWithoutIndexIsLeftAlone(t *testing.T) {
	api, _ := pushSetup(t, map[string]string{"d/sub/a.md": "# A\n"})
	api.seed("r", 1, en("R", ""))
	api.seed("r/sub", 4, en("Hand written", "people wrote this"))
	api.webEdited["r/sub"] = true
	out, r := pushJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/sub") != "unchanged" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	for _, page := range out.Pages {
		if page.Path == "r/sub" && page.Version != 4 {
			t.Fatalf("version %d, want the stored 4", page.Version)
		}
	}
	if api.pages["r/sub"].Translations["EN"].Body != "people wrote this" {
		t.Fatal("a directory without index file must not blank its page")
	}
	inputs, _ := api.puts()
	for _, in := range inputs {
		if in.Path == "r/sub" {
			t.Fatal("no upsert may be sent for an existing directory page")
		}
	}
	api.getFails = 500
	if _, r := pushJSON(t, "d", "r"); r.Exit != ExitFailure {
		t.Fatalf("a failing read of the directory page: exit %d", r.Exit)
	}
}

func TestPushDirectoryPageWithoutIndexIsCreatedWhenMissing(t *testing.T) {
	api, _ := pushSetup(t, map[string]string{"d/sub/a.md": "# A\n"})
	api.seed("r", 1, en("R", ""))
	out, r := pushJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/sub") != "created" || api.pages["r/sub"].Translations["EN"].Title != "sub" {
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

func TestPushUploadsAssetsPerPageAndRewritesLinks(t *testing.T) {
	api, _ := pushSetup(t, assetLayout())
	api.seed("r", 1, en("R", ""))
	out, r := pushJSON(t, "d", "r")
	if r.Exit != 0 || out.Counts["created"] != 2 || out.outcome(t, "r") != "unchanged" {
		t.Fatalf("exit %d stderr %q report %+v", r.Exit, r.Stderr, out)
	}
	if len(api.uploads) != 3 {
		t.Fatalf("uploads = %+v, want the logo for each page and the spec once", api.uploads)
	}
	for _, u := range api.uploads {
		if !strings.Contains(u.FileName, "-") || len(u.FileName) < 13 || u.FileName[12] != '-' {
			t.Fatalf("upload name %q must be <12 hex>-<name>", u.FileName)
		}
	}
	a := api.pages["r/a"].Translations["EN"].Body
	b := api.pages["r/b"].Translations["EN"].Body
	if !strings.Contains(a, "![logo](http") || strings.Contains(a, "sig=") || strings.Contains(a, "img/logo.png") {
		t.Fatalf("a body = %q: the link is the unsigned URL", a)
	}
	aLogo := a[strings.Index(a, "(")+1 : strings.Index(a, ")")]
	bLogo := b[strings.Index(b, "(")+1 : strings.Index(b, ")")]
	if !strings.Contains(aLogo, "/"+api.pages["r/a"].ID+"/") || !strings.Contains(bLogo, "/"+api.pages["r/b"].ID+"/") || !strings.Contains(b, "[spec](http") {
		t.Fatalf("a logo %q, b body %q: every page links its own copy", aLogo, b)
	}
	for _, c := range api.calls {
		if c.Method == "POST" && c.Org != "org-1" {
			t.Fatalf("upload acted in %q", c.Org)
		}
	}

	uploadsBefore := len(api.uploads)
	out, r = pushJSON(t, "d", "r")
	if r.Exit != 0 || out.Counts["unchanged"] != 3 || len(api.uploads) != uploadsBefore {
		t.Fatalf("second run: exit %d counts %+v uploads %d", r.Exit, out.Counts, len(api.uploads))
	}
}

func TestPushChangedAssetGetsANewNameAndUpload(t *testing.T) {
	api, work := pushSetup(t, assetLayout())
	api.seed("r", 1, en("R", ""))
	run(t, "push", "d", "r")
	first := len(api.uploads)
	writeTree(t, work, map[string]string{"d/img/logo.png": "LOGO v2"})
	out, r := pushJSON(t, "d", "r")
	if r.Exit != 0 || len(api.uploads) != first+2 || out.outcome(t, "r/a") != "updated" || out.outcome(t, "r/b") != "updated" {
		t.Fatalf("exit %d uploads %d report %+v", r.Exit, len(api.uploads), out)
	}
}

func TestPushSameFileTwiceOnOnePageUploadsOnce(t *testing.T) {
	api, _ := pushSetup(t, map[string]string{
		"d/a.md":     "# A\n![one](l.png) ![two](./l.png) ![copy](copy.png)\n",
		"d/l.png":    "LOGO",
		"d/copy.png": "LOGO",
	})
	api.seed("r", 1, en("R", ""))
	out, r := pushJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/a") != "created" {
		t.Fatalf("exit %d stderr %q report %+v", r.Exit, r.Stderr, out)
	}
	if len(api.uploads) != 1 {
		t.Fatalf("uploads = %+v, want one copy for the page", api.uploads)
	}
	body := api.pages["r/a"].Translations["EN"].Body
	link := api.srv.URL + "/files/" + api.pages["r/a"].ID + "/"
	if strings.Count(body, "]("+link) != 3 {
		t.Fatalf("body = %q: every link points at the page's one copy", body)
	}
}

func TestPushAssetProblemsFailOnlyThatPage(t *testing.T) {
	api, work := pushSetup(t, map[string]string{"d/ok.md": "# Ok\n", "d/bad.md": "# Bad\n![x](zero.png)\n", "d/zero.png": ""})
	api.seed("r", 1, en("R", ""))
	out, r := pushJSON(t, "d", "r")
	_ = work
	if r.Exit != ExitFailure || out.outcome(t, "r/bad") != "failed" || out.outcome(t, "r/ok") != "created" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	if api.pages["r/bad"] != nil {
		t.Fatal("a page with an unusable asset must not be written")
	}
}

func TestPushDuplicateContentOnTheSamePageFailsWithHint(t *testing.T) {
	api, _ := pushSetup(t, map[string]string{"d/a.md": "# A\n![x](l.png)\n", "d/l.png": "LOGO"})
	api.seed("r", 1, en("R", ""))
	// The API refuses the bytes as a file the page already holds.
	api.hashes[sha256Hex("LOGO")] = true
	out, r := pushJSON(t, "d", "r")
	if r.Exit != ExitFailure || out.outcome(t, "r/a") != "failed" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	for _, page := range out.Pages {
		if page.Path != "r/a" {
			continue
		}
		for _, want := range []string{"upload ", "l.png: ", "this Kalkan page already holds a file with the same content", "duplicate content"} {
			if page.Error.Code != "STG_VAL_005" || !strings.Contains(page.Error.Message, want) {
				t.Fatalf("error %+v lacks %q", page.Error, want)
			}
		}
	}
}

func TestPushJSONPrintsEachPageErrorOnceOnStderr(t *testing.T) {
	api, work := pushSetup(t, docsLayout)
	seedDocs(api)
	api.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n"})
	out, r := pushJSON(t, "docs", "eng/docs")
	if r.Exit != ExitConflict || out.outcome(t, "eng/docs/guide") != "conflict" {
		t.Fatalf("exit %d report %+v", r.Exit, out)
	}
	if strings.Count(r.Stderr, "\n") != 1 {
		t.Fatalf("stderr %q: the error of a page prints once", r.Stderr)
	}
	var e struct {
		Error struct{ Code, Message string }
	}
	decodeJSON(t, r.Stderr, &e)
	if e.Error.Code != "KLK_STA_004" || !strings.HasPrefix(e.Error.Message, "eng/docs/guide: ") {
		t.Fatalf("stderr error %+v", e.Error)
	}
}

func TestPushExistingPageWithAssetsListsAttachmentsFirst(t *testing.T) {
	api, _ := pushSetup(t, assetLayout())
	api.seed("r", 1, en("R", ""))
	run(t, "push", "d", "r")
	// A web edit changed the body; the assets are still attached and are not uploaded again.
	api.pages["r/a"].Translations["EN"] = en("A", "different")
	uploads := len(api.uploads)
	out, r := pushJSON(t, "d", "r")
	if r.Exit != 0 || out.outcome(t, "r/a") != "updated" || len(api.uploads) != uploads {
		t.Fatalf("exit %d uploads %d report %+v", r.Exit, len(api.uploads), out)
	}
	if api.count("GET", "/storage/attachments") == 0 {
		t.Fatal("attachments of an existing page must be listed")
	}
}

func TestPushTextOutput(t *testing.T) {
	api, work := pushSetup(t, docsLayout)
	seedDocs(api)
	api.webEdited["eng/docs/guide"] = true
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n"})
	r := run(t, "push", "docs", "eng/docs")
	if r.Exit != ExitConflict {
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

func TestPushTextPrintsARunLevelErrorOnce(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	api.listFails = 500
	r := run(t, "push", "docs", "eng/docs")
	if r.Exit != ExitFailure || !strings.Contains(r.Stdout, "4 pages: 4 skipped") {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	if strings.Count(r.Stderr, "\n") != 1 {
		t.Fatalf("stderr %q: the run-level error prints once, the skipped pages add nothing", r.Stderr)
	}
	// Every skipped line names the run-level reason, with the code stderr shows for it.
	code := r.Stderr[strings.LastIndex(r.Stderr, "(")+1 : strings.LastIndex(r.Stderr, ")")]
	for _, path := range []string{"eng/docs", "eng/docs/guide", "eng/docs/sub", "eng/docs/sub/deep"} {
		if !strings.Contains(r.Stdout, "skipped   "+path+": not pushed: ") {
			t.Errorf("stdout lacks the cause of %s:\n%s", path, r.Stdout)
		}
	}
	if strings.Count(r.Stdout, "("+code+")\n") != 4 {
		t.Errorf("stdout %q: each skipped line carries the run-level code %s", r.Stdout, code)
	}
}

func TestPushTextPrintsAParentErrorOnceForItsSkippedSubtree(t *testing.T) {
	api, _ := pushSetup(t, docsLayout)
	seedDocs(api)
	api.pages["eng/docs/sub"].Translations["EN"] = en("old", "x")
	writeTree(t, mustGetwd(t), map[string]string{"docs/sub/index.md": "# Sub\nnew\n"})
	api.status["eng/docs/sub"] = 403
	r := run(t, "push", "docs", "eng/docs")
	want := "skipped   eng/docs/sub/deep: not pushed: eng/docs/sub above it was not written (PARENT_NOT_WRITTEN)\n"
	if r.Exit != ExitDenied || !strings.Contains(r.Stdout, want) {
		t.Fatalf("exit %d stdout %q", r.Exit, r.Stdout)
	}
	if strings.Count(r.Stderr, "\n") != 1 || !strings.HasPrefix(r.Stderr, "Error: eng/docs/sub: ") {
		t.Fatalf("stderr %q: only the denied page's error prints", r.Stderr)
	}
}

func TestPushJSONErrorShape(t *testing.T) {
	api, work := pushSetup(t, docsLayout)
	seedDocs(api)
	api.status["eng/docs/guide"] = 403
	writeTree(t, work, map[string]string{"docs/guide.md": "# Guide\nchanged\n"})
	r := run(t, "push", "docs", "eng/docs", "--json")
	var raw map[string]json.RawMessage
	decodeJSON(t, r.Stdout, &raw)
	if _, ok := raw["counts"]; !ok {
		t.Fatalf("keys %v", raw)
	}
	var out pushOut
	decodeJSON(t, r.Stdout, &out)
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
	if strings.Contains(r.Stdout, `"error":null`) {
		t.Fatal("error is omitted, not null, on success")
	}
}

func TestPushNoPromptWhenNotSignedIn(t *testing.T) {
	work := isolate(t)
	writeTree(t, work, docsLayout)
	r := runWithInput(t, "", "push", "docs", "eng/docs")
	if r.Exit != ExitNotSignedIn {
		t.Fatalf("exit %d", r.Exit)
	}
}
