package seed

import (
	"bytes"
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// mainLayout places the clone's core pack in base and its app pack in web, the shared rules and
// one skill of app-one in web and its other files in app-one, and mounts tool in tools/tool.
const mainLayout = `packs:
  - name: base
  - name: web
    parent: base
  - name: app-one
    parent: web
  - name: tool
    parent: base
repositories:
  - name: app-one
    packs: [web, app-one, {name: tool, folder: tools/tool}]
rules:
  - pack: base
    paths: ["agents:packs/core/"]
  - pack: web
    paths: ["agents:packs/app/**", "app-one:.agents/rules/shared-*.md", "app-one:.agents/skills/local/"]
  - pack: app-one
    paths: ["app-one:AGENTS.md", "app-one:.agents/", "app-one:.claude/"]
leftOut:
  - "agents:packs/core/hooks/old.sh"
  - "app-one:.agents/hooks/scratch.sh"
`

// layoutClone builds an agents clone with the packs core and app.
func layoutClone(t *testing.T) string {
	t.Helper()
	agents := t.TempDir()
	write(t, agents, "packs/core/rules/base-rule.md", "base rule\n", 0o644)
	write(t, agents, "packs/core/hooks/guard.sh", "#!/bin/sh\nguard\n", 0o755)
	write(t, agents, "packs/core/hooks/old.sh", "#!/bin/sh\nold\n", 0o755)
	write(t, agents, "packs/core/skills/review/SKILL.md", "review\n", 0o644)
	write(t, agents, "packs/app/rules/web-rule.md", "web rule\n", 0o644)
	write(t, agents, "packs/app/skills/forms/SKILL.md", "forms\n", 0o644)
	return agents
}

// layoutRepo is a checkout without .agents/.managed.json whose origin names the repository.
func layoutRepo(t *testing.T, name string) string {
	t.Helper()
	repo := t.TempDir()
	git(t, repo, "init", "-q")
	git(t, repo, "remote", "add", "origin", "https://example.com/acme/"+name+".git")
	return repo
}

// appOne is a checkout of app-one holding its own files, the files the clone delivers to it, and
// the mount folder of tool.
func appOne(t *testing.T) string {
	t.Helper()
	repo := layoutRepo(t, "app-one")
	write(t, repo, "AGENTS.md", "app one\n", 0o644)
	write(t, repo, ".agents/rules/registry.md", "registry\n", 0o644)
	write(t, repo, ".agents/rules/shared-style.md", "style\n", 0o644)
	write(t, repo, ".agents/skills/local/SKILL.md", "local\n", 0o644)
	link(t, repo, ".claude/skills/local", "../../.agents/skills/local")
	write(t, repo, ".claude/settings.json", "{}\n", 0o644)
	write(t, repo, ".agents/hooks/scratch.sh", "#!/bin/sh\nscratch\n", 0o755)
	write(t, repo, "tools/tool/AGENTS.md", "tool\n", 0o644)
	// What the clone delivers, as a checkout holds it.
	write(t, repo, ".agents/rules/base-rule.md", "base rule\n", 0o644)
	write(t, repo, ".agents/hooks/guard.sh", "#!/bin/sh\nguard\n", 0o755)
	write(t, repo, ".agents/hooks/old.sh", "#!/bin/sh\nold\n", 0o755)
	write(t, repo, ".agents/skills/review/SKILL.md", "review\n", 0o644)
	link(t, repo, ".claude/skills/review", "../../.agents/skills/review")
	write(t, repo, ".agents/rules/web-rule.md", "web rule\n", 0o644)
	write(t, repo, ".agents/skills/forms/SKILL.md", "forms\n", 0o644)
	link(t, repo, ".claude/skills/forms", "../../.agents/skills/forms")
	return repo
}

// seedTree writes a layout file and its content folder, by path below content/, and returns the
// layout file.
func seedTree(t *testing.T, layout string, content map[string]string) string {
	t.Helper()
	dir := t.TempDir()
	write(t, dir, "layout.yaml", layout, 0o644)
	for path, text := range content {
		write(t, dir, filepath.Join(ContentDir, filepath.FromSlash(path)), text, 0o644)
	}
	return filepath.Join(dir, "layout.yaml")
}

// mainTree is mainLayout with content that replaces app-one's settings and tool's AGENTS.md and
// adds a rule to base.
func mainTree(t *testing.T) string {
	t.Helper()
	return seedTree(t, mainLayout, map[string]string{
		"app-one/.claude/settings.json": "{\"model\": \"fixed\"}\n",
		"base/.agents/rules/added.md":   "added\n",
		"tool/AGENTS.md":                "tool, from the seed tree\n",
		".DS_Store":                     "litter\n",
	})
}

func setPaths(set client.SeedSet) []string {
	paths := make([]string, 0, len(set.Files))
	for _, file := range set.Files {
		paths = append(paths, file.Path)
	}
	return paths
}

func TestBuildLayout_placesEachFileByItsFirstMatchingRule(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: mainTree(t)})
	if err != nil {
		t.Fatal(err)
	}
	var order []string
	for _, set := range plan.Request.Sets {
		order = append(order, set.Name+"<"+set.Parent)
	}
	if want := []string{"base<", "web<base", "app-one<web", "tool<base"}; !reflect.DeepEqual(order, want) {
		t.Fatalf("sets with their parents = %v, want %v", order, want)
	}
	want := map[string][]string{
		"base": {".agents/hooks/guard.sh", ".agents/rules/added.md", ".agents/rules/base-rule.md",
			".agents/skills/review/SKILL.md", ".claude/skills/review"},
		// The skill link follows its skill into web, although the app-one rule matches its path.
		"web": {".agents/rules/shared-style.md", ".agents/rules/web-rule.md", ".agents/skills/forms/SKILL.md",
			".agents/skills/local/SKILL.md", ".claude/skills/forms", ".claude/skills/local"},
		"app-one": {".agents/rules/registry.md", ".claude/settings.json", "AGENTS.md"},
		"tool":    {"AGENTS.md"},
	}
	for name, paths := range want {
		if got := setPaths(findSet(t, plan, client.KindPack, name)); !reflect.DeepEqual(got, paths) {
			t.Errorf("%s = %v, want %v", name, got, paths)
		}
	}
	local, _ := fileOf(findSet(t, plan, client.KindPack, "web"), ".claude/skills/local")
	if local.Mode != client.ModeSymlink || local.SymlinkTarget != "../../.agents/skills/local" {
		t.Errorf("the skill link = %+v", local)
	}
	if len(plan.Request.Repositories) != 1 {
		t.Fatalf("repositories = %+v", plan.Request.Repositories)
	}
	request := plan.Request.Repositories[0]
	wantPacks := []client.SeedRepositoryPack{{Name: "web"}, {Name: "app-one"}, {Name: "tool", RootPath: "tools/tool"}}
	if request.Name != "app-one" || request.RemoteURL != "https://example.com/acme/app-one.git" || !reflect.DeepEqual(request.Packs, wantPacks) {
		t.Errorf("repository = %+v", request)
	}
	if len(plan.Warnings) != 0 {
		t.Errorf("every rule places a file and every source is read: %v", plan.Warnings)
	}
}

func TestBuildLayout_failsNamingEveryUnplacedFile(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	tree := seedTree(t, `packs: [{name: base}, {name: app-one, parent: base}]
repositories: [{name: app-one, packs: [app-one]}]
rules:
  - pack: base
    paths: ["agents:packs/core/"]
`, nil)
	_, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: tree})
	var placement *PlacementError
	if !errors.As(err, &placement) {
		t.Fatalf("err = %v, want a PlacementError", err)
	}
	// The generated link of the unplaced forms skill is not named: its skill's files are.
	want := []string{
		"agents:packs/app/rules/web-rule.md",
		"agents:packs/app/skills/forms/SKILL.md",
		"app-one:.agents/hooks/scratch.sh",
		"app-one:.agents/rules/registry.md",
		"app-one:.agents/rules/shared-style.md",
		"app-one:.agents/skills/local/SKILL.md",
		"app-one:.claude/settings.json",
		"app-one:.claude/skills/local",
		"app-one:AGENTS.md",
	}
	if !reflect.DeepEqual(placement.Unplaced, want) || len(placement.Conflicts) != 0 {
		t.Fatalf("unplaced = %v, conflicts = %v, want %v", placement.Unplaced, placement.Conflicts, want)
	}
	for _, name := range want {
		if !strings.Contains(err.Error(), name) {
			t.Errorf("the error does not name %s:\n%v", name, err)
		}
	}
}

func TestBuildLayout_contentReplacesOrAddsAPathOfItsPack(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: mainTree(t)})
	if err != nil {
		t.Fatal(err)
	}
	settings, _ := fileOf(findSet(t, plan, client.KindPack, "app-one"), ".claude/settings.json")
	if settings.SHA256 != sum("{\"model\": \"fixed\"}\n") {
		t.Errorf("settings = %+v, want the seed tree's file", settings)
	}
	agentsFile, _ := fileOf(findSet(t, plan, client.KindPack, "tool"), "AGENTS.md")
	if agentsFile.SHA256 != sum("tool, from the seed tree\n") {
		t.Errorf("the mounted pack's AGENTS.md = %+v, want the seed tree's file", agentsFile)
	}
	if blob, ok := plan.Blobs[settings.SHA256]; !ok || !strings.HasSuffix(filepath.ToSlash(blob.Source), "content/app-one/.claude/settings.json") {
		t.Errorf("the blob is uploaded from the seed tree: %+v", blob)
	}
	wantContent := []ContentFile{
		{Pack: "base", Path: ".agents/rules/added.md"},
		{Pack: "app-one", Path: ".claude/settings.json", Replaces: "app-one:.claude/settings.json"},
		{Pack: "tool", Path: "AGENTS.md", Replaces: "app-one:tools/tool/AGENTS.md"},
	}
	if !reflect.DeepEqual(plan.Content, wantContent) {
		t.Errorf("content = %+v, want %+v", plan.Content, wantContent)
	}
	for path, want := range map[string]string{
		"app-one:.claude/settings.json": "Replaced by content/app-one/.claude/settings.json of the seed tree",
		"app-one:tools/tool/AGENTS.md":  "Replaced by content/tool/AGENTS.md of the seed tree",
	} {
		if got := reasonOf(plan.Request.NotImported, path); got != want {
			t.Errorf("%s reason = %q, want %q", path, got, want)
		}
	}
	var out bytes.Buffer
	PrintPlan(&out, plan)
	for _, want := range []string{"From the seed tree (3):",
		"app-one .claude/settings.json: content/app-one/.claude/settings.json in place of app-one:.claude/settings.json",
		"base .agents/rules/added.md: content/base/.agents/rules/added.md, added"} {
		if !strings.Contains(out.String(), want) {
			t.Errorf("missing %q in:\n%s", want, out.String())
		}
	}
}

func TestBuildLayout_refusesContentItCannotSend(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	tree := seedTree(t, mainLayout, map[string]string{
		"other/.agents/rules/x.md": "x\n",
		"base/notes.txt":           "notes\n",
	})
	_, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: tree})
	if err == nil {
		t.Fatal("content of an unknown pack and a path Kaya does not deliver must fail")
	}
	for _, want := range []string{"content/other: not a folder named after a pack", "content/base/notes.txt: " + ReasonNotDelivered} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
}

func TestBuildLayout_leftOutPathsAreReportedAndChangedOnPurpose(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: mainTree(t)})
	if err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{"agents:packs/core/hooks/old.sh", "app-one:.agents/hooks/scratch.sh"} {
		if got := reasonOf(plan.Request.NotImported, path); got != ReasonLeftOut {
			t.Errorf("%s reason = %q, want %q", path, got, ReasonLeftOut)
		}
	}
	if _, ok := fileOf(findSet(t, plan, client.KindPack, "base"), ".agents/hooks/old.sh"); ok {
		t.Error("a left-out file is not sent, although a rule matches it")
	}
	want := map[string]string{
		".agents/hooks/old.sh":     "left out by the seed layout",
		".agents/hooks/scratch.sh": "left out by the seed layout",
		".agents/rules/added.md":   "from content/base/.agents/rules/added.md of the seed tree",
		".claude/settings.json":    "from content/app-one/.claude/settings.json of the seed tree",
		"tools/tool/AGENTS.md":     "from content/tool/AGENTS.md of the seed tree",
	}
	if got := plan.Repositories[0].OnPurpose; !reflect.DeepEqual(got, want) {
		t.Errorf("on purpose = %v, want %v", got, want)
	}
}

func TestCheck_layoutChangesOnPurposeDoNotFailTheSeed(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: mainTree(t)})
	if err != nil {
		t.Fatal(err)
	}
	// The server composes every pack of app-one, with tool's AGENTS.md in its folder.
	release := client.Release{Number: 1, RepositoryID: "repo-1"}
	for _, set := range plan.Request.Sets {
		for _, file := range set.Files {
			path := file.Path
			if set.Name == "tool" && path == "AGENTS.md" {
				path = "tools/tool/AGENTS.md"
			}
			release.Files = append(release.Files, client.ReleaseEntry{Path: path, SHA256: file.SHA256, Mode: file.Mode, Target: file.SymlinkTarget})
		}
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/kaya/repositories":
			answer(w, http.StatusOK, []client.Repository{{ID: "repo-1", Name: "app-one",
				RemoteURL: NormalizeRemote("https://example.com/acme/app-one.git"), Status: "ACTIVE"}}, nil)
		case "/kaya/repositories/repo-1/releases/1":
			answer(w, http.StatusOK, release, nil)
		default:
			t.Errorf("unexpected %s %s", r.Method, r.URL)
			http.NotFound(w, r)
		}
	}))
	defer server.Close()
	api := client.New(server.URL, "org-1", func(context.Context) (string, error) { return "jwt", nil })
	run := client.SeedRun{ID: "run-1", Status: client.SeedPassed, Report: client.SeedReport{Applied: true,
		Releases: []client.SeedReleaseReport{{RepositoryID: "repo-1", Number: 1}}}}

	checks, err := Check(context.Background(), api, plan, run)
	if err != nil {
		t.Fatal(err)
	}
	if len(checks) != 1 || len(checks[0].Mismatches) != 0 || len(checks[0].OnPurpose) != 5 {
		t.Fatalf("checks = %+v, want five paths changed on purpose and no mismatch", checks)
	}
	if !Passed(run, checks) {
		t.Error("paths the layout changes on purpose do not fail the seed")
	}
	var out bytes.Buffer
	PrintRun(&out, run, plan, checks)
	for _, want := range []string{"app-one: release 1 equals the disk apart from 5 paths changed on purpose",
		"changed on purpose:", ".agents/hooks/scratch.sh: on disk, not in the release, left out by the seed layout",
		"From the seed tree (3):"} {
		if !strings.Contains(out.String(), want) {
			t.Errorf("missing %q in:\n%s", want, out.String())
		}
	}

	// A difference the layout does not make still fails.
	write(t, repo, ".agents/rules/registry.md", "registry, edited\n", 0o644)
	plan, err = Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: mainTree(t)})
	if err != nil {
		t.Fatal(err)
	}
	checks, err = Check(context.Background(), api, plan, run)
	if err != nil {
		t.Fatal(err)
	}
	if len(checks) != 1 || len(checks[0].Mismatches) != 1 || checks[0].Mismatches[0].Path != ".agents/rules/registry.md" || Passed(run, checks) {
		t.Fatalf("checks = %+v, want the edited rule as a mismatch", checks)
	}
}

func TestBuildLayout_secondRunAddsARepository(t *testing.T) {
	agents, one := layoutClone(t), layoutRepo(t, "app-one")
	write(t, one, "AGENTS.md", "app one\n", 0o644)
	two := layoutRepo(t, "app-two")
	write(t, two, "AGENTS.md", "app two\n", 0o644)
	write(t, two, "tools/tool/AGENTS.md", "tool\n", 0o644)
	tree := seedTree(t, `packs:
  - name: base
  - {name: web, parent: base}
  - {name: app-one, parent: web}
  - {name: app-two, parent: web}
  - {name: tool, parent: base}
repositories:
  - {name: app-one, packs: [web, app-one]}
  - name: app-two
    packs: [web, app-two, {name: tool, folder: tools/tool}]
rules:
  - {pack: base, paths: ["agents:packs/core/"]}
  - {pack: web, paths: ["agents:packs/app/"]}
  - {pack: app-one, paths: ["app-one:AGENTS.md"]}
  - {pack: app-two, paths: ["app-two:AGENTS.md"]}
`, nil)

	first, err := Build(Options{AgentsDir: agents, RepoDirs: []string{one}, SourceRef: "ref", Layout: tree})
	if err != nil {
		t.Fatal(err)
	}
	var sent []string
	for _, set := range first.Request.Sets {
		sent = append(sent, set.Name)
	}
	if want := []string{"base", "web", "app-one"}; !reflect.DeepEqual(sent, want) {
		t.Errorf("first run sends %v, want %v: app-two and tool take files from app-two, which it does not read", sent, want)
	}
	if len(first.Request.Repositories) != 1 || first.Request.Repositories[0].Name != "app-one" {
		t.Errorf("first run repositories = %+v, want app-one only", first.Request.Repositories)
	}
	for _, want := range []string{"the pack app-two is not sent", "the pack tool is not sent", "the repository app-two is not read"} {
		if !strings.Contains(strings.Join(first.Warnings, "\n"), want) {
			t.Errorf("missing warning %q in %v", want, first.Warnings)
		}
	}

	second, err := Build(Options{AgentsDir: agents, RepoDirs: []string{one, two}, SourceRef: "ref", Layout: tree})
	if err != nil {
		t.Fatal(err)
	}
	sent = nil
	for _, set := range second.Request.Sets {
		sent = append(sent, set.Name)
	}
	if want := []string{"base", "web", "app-one", "app-two", "tool"}; !reflect.DeepEqual(sent, want) {
		t.Errorf("second run sends %v, want %v", sent, want)
	}
	if len(second.Request.Repositories) != 2 || second.Request.Repositories[1].Name != "app-two" {
		t.Errorf("second run repositories = %+v, want app-one and app-two", second.Request.Repositories)
	}
	// Kaya leaves a pack whose files are equal as it is, so the second run must send the same files.
	for _, name := range []string{"base", "web", "app-one"} {
		if a, b := findSet(t, first, client.KindPack, name), findSet(t, second, client.KindPack, name); !reflect.DeepEqual(a, b) {
			t.Errorf("%s differs between the runs: %+v and %+v", name, a, b)
		}
	}
	if len(second.Warnings) != 0 {
		t.Errorf("second run warnings = %v", second.Warnings)
	}

	// Without the clone, the packs that take its files keep what Kaya holds.
	repoOnly, err := Build(Options{RepoDirs: []string{one, two}, SourceRef: "ref", Layout: tree})
	if err != nil {
		t.Fatal(err)
	}
	sent = nil
	for _, set := range repoOnly.Request.Sets {
		sent = append(sent, set.Name)
	}
	if want := []string{"app-one", "app-two", "tool"}; !reflect.DeepEqual(sent, want) {
		t.Errorf("a run without the clone sends %v, want %v", sent, want)
	}
}

func TestBuildLayout_skillAndItsLinkBelongToOnePack(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	split := strings.Replace(mainLayout, `"app-one:.agents/skills/local/"`, `"app-one:.agents/skills/local/SKILL.md"`, 1)
	write(t, repo, ".agents/skills/local/notes.md", "notes\n", 0o644)
	_, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: seedTree(t, split, nil)})
	var placement *PlacementError
	if !errors.As(err, &placement) || len(placement.Conflicts) != 1 || !strings.Contains(placement.Conflicts[0], "the skill local of app-one go to the packs app-one, web") {
		t.Fatalf("err = %v, want the split skill named", err)
	}

	// A skill left out takes its link with it.
	leftOut := strings.Replace(mainLayout, `  - "app-one:.agents/hooks/scratch.sh"`, "  - \"app-one:.agents/hooks/scratch.sh\"\n  - \"app-one:.agents/skills/local/\"", 1)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: seedTree(t, leftOut, nil)})
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"web", "app-one"} {
		if _, ok := fileOf(findSet(t, plan, client.KindPack, name), ".claude/skills/local"); ok {
			t.Errorf("%s holds the link of a skill that is left out", name)
		}
	}
	if got := reasonOf(plan.Request.NotImported, "app-one:.claude/skills/local"); got != ReasonLeftOut {
		t.Errorf("the link's reason = %q, want %q", got, ReasonLeftOut)
	}
}

func TestBuildLayout_twoFilesForOnePathOfAPackConflict(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	write(t, repo, ".agents/rules/base-rule.md", "base rule, edited\n", 0o644)
	clash := strings.Replace(mainLayout, `paths: ["agents:packs/core/"]`, `paths: ["agents:packs/core/", "app-one:.agents/rules/base-rule.md"]`, 1)
	_, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "ref", Layout: seedTree(t, clash, nil)})
	var placement *PlacementError
	if !errors.As(err, &placement) || len(placement.Conflicts) != 1 ||
		!strings.Contains(placement.Conflicts[0], "the pack base gets .agents/rules/base-rule.md from both agents:packs/core/rules/base-rule.md and app-one:.agents/rules/base-rule.md") {
		t.Fatalf("err = %v, want the two files named", err)
	}
}

func TestBuildLayout_refusesWhatItCannotPlace(t *testing.T) {
	agents, repo := layoutClone(t), appOne(t)
	tree := mainTree(t)
	other := layoutRepo(t, "app-three")
	cases := map[string]struct {
		opts Options
		want string
	}{
		"mounts":         {Options{Mounts: []Mount{{Name: "tool", Root: "tools/tool"}}}, "--pack does not apply"},
		"parents":        {Options{Parents: map[string]string{"web": "base"}}, "--pack-parent does not apply"},
		"repo packs":     {Options{RepoPacks: map[string]string{"": "web"}}, "--repo-pack does not apply"},
		"after cutover":  {Options{AfterCutover: true}, "--after-cutover does not apply"},
		"unnamed repo":   {Options{RepoDirs: []string{other}}, "the repository app-three, which the layout does not name"},
		"no repository":  {Options{RepoDirs: []string{}}, "--repo"},
		"missing folder": {Options{Layout: seedTree(t, strings.Replace(mainLayout, "folder: tools/tool", "folder: tools/gone", 1), nil)}, "mounts the pack tool in tools/gone"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			opts := tc.opts
			opts.AgentsDir, opts.SourceRef = agents, "ref"
			if opts.RepoDirs == nil {
				opts.RepoDirs = []string{repo}
			}
			if opts.Layout == "" {
				opts.Layout = tree
			}
			if _, err := Build(opts); err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("err = %v, want %q", err, tc.want)
			}
		})
	}
}

func TestReadLayout_refusesAnInvalidLayout(t *testing.T) {
	cases := map[string]struct {
		layout, want string
	}{
		"empty":            {"", "is empty"},
		"no pack":          {"rules: []\n", "names no pack"},
		"unknown key":      {"packs: [{name: base, colour: red}]\n", "field colour not found"},
		"unknown pack key": {"packs: [{name: base}]\nrepositories: [{name: app-one, packs: [{name: base, root: x}]}]\n", "field root not found"},
		"two documents":    {"packs: [{name: base}]\n---\npacks: [{name: web}]\n", "more than one YAML document"},
		"bad pack name":    {"packs: [{name: Base}]\n", "the name \"Base\""},
		"pack twice":       {"packs: [{name: base}, {name: base}]\n", "named twice"},
		"unknown parent":   {"packs: [{name: web, parent: base}]\n", "the parent base of the pack web is not a pack"},
		"cycle":            {"packs: [{name: a, parent: b}, {name: b, parent: a}]\n", "form a cycle"},
		"repository pack":  {"packs: [{name: base}]\nrepositories: [{name: app-one, packs: [web]}]\n", "\"web\" is not a pack"},
		"repository twice": {"packs: [{name: base}]\nrepositories: [{name: app-one, packs: [base]}, {name: app-one, packs: [base]}]\n", "named twice"},
		"agents repository": {"packs: [{name: base}]\nrepositories: [{name: agents, packs: [base]}]\n",
			"no repository can take that name"},
		"bad folder": {"packs: [{name: base}, {name: tool}]\nrepositories: [{name: app-one, packs: [base, {name: tool, folder: ../x}]}]\n",
			"the folder \"../x\""},
		"nested folders": {"packs: [{name: a}, {name: b}]\nrepositories: [{name: app-one, packs: [{name: a, folder: tools}, {name: b, folder: tools/b}]}]\n",
			"overlap"},
		"rule pack":         {"packs: [{name: base}]\nrules: [{pack: web, paths: [\"agents:packs/x/\"]}]\n", "\"web\" is not a pack"},
		"rule without path": {"packs: [{name: base}]\nrules: [{pack: base}]\n", "names no path"},
		"no source":         {"packs: [{name: base}]\nrules: [{pack: base, paths: [\"packs/x/\"]}]\n", "give <source>:<path>"},
		"unknown source":    {"packs: [{name: base}]\nrules: [{pack: base, paths: [\"app-nine:x\"]}]\n", "neither agents nor a repository"},
		"bad glob":          {"packs: [{name: base}]\nrules: [{pack: base, paths: [\"agents:packs/[\"]}]\n", "syntax error in pattern"},
		"double star":       {"packs: [{name: base}]\nrules: [{pack: base, paths: [\"agents:packs/**.md\"]}]\n", "segment of its own"},
		"dot dot":           {"packs: [{name: base}]\nleftOut: [\"agents:packs/../x\"]\n", "'..'"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			file := filepath.Join(t.TempDir(), "layout.yaml")
			if err := os.WriteFile(file, []byte(tc.layout), 0o600); err != nil {
				t.Fatal(err)
			}
			if _, err := readLayout(file); err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("err = %v, want %q", err, tc.want)
			}
		})
	}
}

func TestPathPattern_matches(t *testing.T) {
	l := &seedLayout{repos: map[string]layoutRepository{"app-one": {Name: "app-one"}}}
	cases := []struct {
		pattern, source, path string
		want                  bool
	}{
		{"agents:packs/core/", "agents", "packs/core/rules/a.md", true},
		{"agents:packs/core/", "agents", "packs/corex/rules/a.md", false},
		{"agents:packs/core/", "app-one", "packs/core/rules/a.md", false},
		{"agents:packs/*/rules/*.md", "agents", "packs/app/rules/a.md", true},
		{"agents:packs/*/rules/*.md", "agents", "packs/app/rules/deep/a.md", false},
		{"agents:packs/**/a.md", "agents", "packs/a.md", true},
		{"agents:packs/**/a.md", "agents", "packs/x/y/a.md", true},
		{"app-one:AGENTS.md", "app-one", "AGENTS.md", true},
		{"app-one:AGENTS.md", "app-one", "tools/AGENTS.md", false},
		{"app-one:**/AGENTS.md", "app-one", "tools/tool/AGENTS.md", true},
		{"app-one:.agents/rules/shared-?.md", "app-one", ".agents/rules/shared-a.md", true},
		{"app-one:.agents/rules/[a-c].md", "app-one", ".agents/rules/d.md", false},
	}
	for _, tc := range cases {
		pattern, err := l.parsePattern(tc.pattern)
		if err != nil {
			t.Fatalf("%s: %v", tc.pattern, err)
		}
		if got := pattern.matches(sourceFile{source: tc.source, path: tc.path}); got != tc.want {
			t.Errorf("%s matches %s:%s = %v, want %v", tc.pattern, tc.source, tc.path, got, tc.want)
		}
	}
}
