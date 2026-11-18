package seed

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/kaya-cli/internal/client"
)

func sum(content string) string {
	digest := sha256.Sum256([]byte(content))
	return hex.EncodeToString(digest[:])
}

func write(t *testing.T, root, rel, content string, mode os.FileMode) {
	t.Helper()
	abs := filepath.Join(root, filepath.FromSlash(rel))
	if err := os.MkdirAll(filepath.Dir(abs), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(abs, []byte(content), mode); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(abs, mode); err != nil {
		t.Fatal(err)
	}
}

func link(t *testing.T, root, rel, target string) {
	t.Helper()
	abs := filepath.Join(root, filepath.FromSlash(rel))
	if err := os.MkdirAll(filepath.Dir(abs), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, abs); err != nil {
		t.Fatal(err)
	}
}

func git(t *testing.T, dir string, args ...string) {
	t.Helper()
	cmd := exec.Command("git", append([]string{"-C", dir}, args...)...)
	cmd.Env = append(os.Environ(), "GIT_AUTHOR_NAME=t", "GIT_AUTHOR_EMAIL=t@example.com",
		"GIT_COMMITTER_NAME=t", "GIT_COMMITTER_EMAIL=t@example.com")
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("git %v: %v\n%s", args, err, out)
	}
}

// fixture builds an agents clone with packs org and village, and a consumer repository that
// takes both and holds its own rule, a skill link, an adapter link and files Kaya never delivers.
func fixture(t *testing.T) (agents, repo string) {
	t.Helper()
	agents = t.TempDir()
	write(t, agents, "packs/org/rules/core.md", "core\n", 0o644)
	write(t, agents, "packs/org/hooks/dispatch.sh", "#!/bin/sh\n", 0o755)
	write(t, agents, "packs/org/skills/kaizen/SKILL.md", "kaizen\n", 0o644)
	write(t, agents, "packs/org/agents/coding-engineer.md", "engineer\n", 0o644)
	write(t, agents, "packs/org/README.md", "pack readme\n", 0o644)
	write(t, agents, "packs/village/plugins/mod/plugin.json", "{}\n", 0o644)
	write(t, agents, "tools/release.py", "print()\n", 0o644)
	git(t, agents, "init", "-q")
	git(t, agents, "add", ".")
	git(t, agents, "commit", "-q", "-m", "packs")

	repo = t.TempDir()
	write(t, repo, ".agents/rules/core.md", "core\n", 0o644)
	write(t, repo, ".agents/hooks/dispatch.sh", "#!/bin/sh\n", 0o755)
	write(t, repo, ".agents/skills/kaizen/SKILL.md", "kaizen\n", 0o644)
	link(t, repo, ".claude/skills/kaizen", "../../.agents/skills/kaizen")
	write(t, repo, ".claude/agents/coding-engineer.md", "engineer, edited here\n", 0o644)
	write(t, repo, ".claude/skills/mod/plugin.json", "{}\n", 0o644)
	write(t, repo, ".agents/rules/00-project-registry.md", "registry\n", 0o644)
	write(t, repo, ".agents/AGENTS.md", "agents\n", 0o644)
	link(t, repo, "AGENTS.md", ".agents/AGENTS.md")
	link(t, repo, ".claude/rules", "../.agents/rules")
	link(t, repo, ".claude/templates", "../.agents/templates")
	write(t, repo, ".claude/settings.json", "{}\n", 0o644)
	write(t, repo, ".agents/memory/lessons.md", "lessons\n", 0o644)
	write(t, repo, ".mcp.json", "{}\n", 0o644)
	write(t, repo, ".claude/worktrees/x/AGENTS.md", "a checkout\n", 0o644)
	managed := Managed{
		Packs: []string{"org", "village"},
		Files: map[string]string{
			".agents/rules/core.md":             sum("core\n"),
			".agents/hooks/dispatch.sh":         sum("#!/bin/sh\n"),
			".agents/skills/kaizen/SKILL.md":    sum("kaizen\n"),
			".claude/skills/kaizen":             "symlink:../../.agents/skills/kaizen",
			".claude/agents/coding-engineer.md": sum("engineer\n"),
			".claude/skills/mod/plugin.json":    sum("{}\n"),
		},
	}
	data, err := json.Marshal(managed)
	if err != nil {
		t.Fatal(err)
	}
	write(t, repo, ".agents/.managed.json", string(data), 0o644)
	git(t, repo, "init", "-q")
	git(t, repo, "remote", "add", "origin", "https://user:secret@github.com/senseylabs/Village.git")
	return agents, repo
}

// writeManaged rewrites the pack list of .agents/.managed.json of repo and returns the file.
func writeManaged(t *testing.T, repo string, packs []string) Managed {
	t.Helper()
	managed, err := ReadManaged(repo)
	if err != nil {
		t.Fatal(err)
	}
	managed.Packs = packs
	writeManagedFile(t, repo, managed)
	return managed
}

func writeManagedFile(t *testing.T, repo string, managed Managed) {
	t.Helper()
	data, err := json.Marshal(managed)
	if err != nil {
		t.Fatal(err)
	}
	write(t, repo, ".agents/.managed.json", string(data), 0o644)
}

func findSet(t *testing.T, plan Plan, kind, name string) client.SeedSet {
	t.Helper()
	for _, set := range plan.Request.Sets {
		if set.Kind == kind && set.Name == name {
			return set
		}
	}
	t.Fatalf("no %s set %s in %+v", kind, name, plan.Request.Sets)
	return client.SeedSet{}
}

func fileOf(set client.SeedSet, path string) (client.SeedFile, bool) {
	for _, file := range set.Files {
		if file.Path == path {
			return file, true
		}
	}
	return client.SeedFile{}, false
}

func reasonOf(items []client.NotImported, path string) string {
	for _, item := range items {
		if item.Path == path {
			return item.Reason
		}
	}
	return ""
}

func TestBuild_mapsPacksToConsumerPaths(t *testing.T) {
	agents, repo := fixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	org := findSet(t, plan, client.KindPack, "org")
	if org.Parent != "" {
		t.Fatalf("org is the root of the tree: %+v", org)
	}
	cases := map[string]client.SeedFile{
		".agents/rules/core.md":             {Path: ".agents/rules/core.md", SHA256: sum("core\n"), Mode: client.ModeFile},
		".agents/hooks/dispatch.sh":         {Path: ".agents/hooks/dispatch.sh", SHA256: sum("#!/bin/sh\n"), Mode: client.ModeExecutable},
		".agents/skills/kaizen/SKILL.md":    {Path: ".agents/skills/kaizen/SKILL.md", SHA256: sum("kaizen\n"), Mode: client.ModeFile},
		".claude/skills/kaizen":             {Path: ".claude/skills/kaizen", Mode: client.ModeSymlink, SymlinkTarget: "../../.agents/skills/kaizen"},
		".claude/agents/coding-engineer.md": {Path: ".claude/agents/coding-engineer.md", SHA256: sum("engineer\n"), Mode: client.ModeFile},
	}
	for path, want := range cases {
		got, ok := fileOf(org, path)
		if !ok || got != want {
			t.Errorf("org %s = %+v, want %+v", path, got, want)
		}
	}
	village := findSet(t, plan, client.KindPack, "village")
	if village.Parent != "org" {
		t.Errorf("village parent = %q, want org", village.Parent)
	}
	if _, ok := fileOf(village, ".claude/skills/mod/plugin.json"); !ok {
		t.Errorf("a plugin is delivered as real files under .claude/skills: %+v", village.Files)
	}
	if got := reasonOf(plan.Request.NotImported, "agents:packs/org/README.md"); got != ReasonPackExtra {
		t.Errorf("pack readme reason = %q", got)
	}
	if got := reasonOf(plan.Request.NotImported, "agents:tools/"); got != ReasonAgentsRepo {
		t.Errorf("agents tooling reason = %q", got)
	}
}

func TestBuild_repositoryFilesFoldIntoItsFamilyPack(t *testing.T) {
	agents, repo := fixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	if len(plan.Request.Sets) != 2 {
		t.Fatalf("sets = %+v, want org and village only", plan.Request.Sets)
	}
	ws := findSet(t, plan, client.KindPack, "village")
	if len(plan.Request.Repositories) != 1 {
		t.Fatalf("repositories = %+v", plan.Request.Repositories)
	}
	repository := plan.Request.Repositories[0]
	if repository.RemoteURL != "https://github.com/senseylabs/Village.git" || repository.Name != "village" {
		t.Errorf("repository = %+v; the password must never be sent", repository)
	}
	wantPacks := []client.SeedRepositoryPack{{Name: "org"}, {Name: "village"}}
	if !reflect.DeepEqual(repository.Packs, wantPacks) {
		t.Errorf("packs = %+v, want %+v in order", repository.Packs, wantPacks)
	}
	if plan.Repositories[0].FamilyPack != "village" {
		t.Errorf("family pack = %q", plan.Repositories[0].FamilyPack)
	}
	var paths []string
	for _, file := range ws.Files {
		paths = append(paths, file.Path)
	}
	// The pack's own plugin from the clone, then the repository's own files.
	want := []string{
		".agents/AGENTS.md", ".agents/rules/00-project-registry.md", ".claude/rules", ".claude/settings.json",
		".claude/skills/mod/plugin.json", ".claude/templates", "AGENTS.md",
	}
	if !reflect.DeepEqual(paths, want) {
		t.Fatalf("village files = %v, want %v", paths, want)
	}
	if _, ok := fileOf(findSet(t, plan, client.KindPack, "org"), ".agents/rules/00-project-registry.md"); ok {
		t.Error("org received a file of the repository")
	}
	if agentsLink, _ := fileOf(ws, "AGENTS.md"); agentsLink.Mode != client.ModeSymlink || agentsLink.SymlinkTarget != ".agents/AGENTS.md" {
		t.Errorf("AGENTS.md = %+v, want a link to .agents/AGENTS.md", agentsLink)
	}
	for path, target := range map[string]string{".claude/rules": "../.agents/rules", ".claude/templates": "../.agents/templates"} {
		if toolLink, _ := fileOf(ws, path); toolLink.Mode != client.ModeSymlink || toolLink.SymlinkTarget != target {
			t.Errorf("%s = %+v, want a link to %s in the family pack", path, toolLink, target)
		}
	}
	for path, reason := range map[string]string{
		"village:.agents/memory/lessons.md": ReasonLessons,
		"village:.agents/.managed.json":     ReasonManagedState,
		"village:.claude/worktrees/":        ReasonWorktrees,
	} {
		if got := reasonOf(plan.Request.NotImported, path); got != reason {
			t.Errorf("%s reason = %q, want %q", path, got, reason)
		}
	}
	for _, item := range plan.Request.NotImported {
		if item.Path == "village:.claude/worktrees/x/AGENTS.md" {
			t.Errorf("a worktree is reported once, never walked")
		}
	}
	if _, ok := plan.Blobs[sum("registry\n")]; !ok {
		t.Errorf("the repository's own file must be an upload candidate")
	}
}

func TestBuild_repositoryFileWinsAPathItsFamilyPackHolds(t *testing.T) {
	agents, repo := fixture(t)
	write(t, agents, "packs/village/rules/registry.md", "pack registry\n", 0o644)
	git(t, agents, "add", ".")
	git(t, agents, "commit", "-q", "-m", "registry")
	write(t, repo, ".agents/rules/registry.md", "repository registry\n", 0o644)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	file, _ := fileOf(findSet(t, plan, client.KindPack, "village"), ".agents/rules/registry.md")
	if file.SHA256 != sum("repository registry\n") {
		t.Errorf("registry = %+v, want the repository's copy", file)
	}
	if len(plan.Warnings) != 1 || !strings.Contains(plan.Warnings[0], "village:.agents/rules/registry.md") {
		t.Errorf("warnings = %v, want the clash named", plan.Warnings)
	}
}

func TestBuild_repositoryWithoutFamilyPackGetsAPackNamedAfterIt(t *testing.T) {
	agents, repo := fixture(t)
	git(t, repo, "remote", "set-url", "origin", "git@github.com:senseylabs/acme.git")
	writeManaged(t, repo, []string{"org"})
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	own := findSet(t, plan, client.KindPack, "acme")
	if own.Parent != "org" {
		t.Errorf("acme parent = %q, want org", own.Parent)
	}
	if _, ok := fileOf(own, ".agents/rules/00-project-registry.md"); !ok {
		t.Errorf("the repository's own rule is missing: %+v", own.Files)
	}
	wantPacks := []client.SeedRepositoryPack{{Name: "org"}, {Name: "acme"}}
	if !reflect.DeepEqual(plan.Request.Repositories[0].Packs, wantPacks) {
		t.Errorf("packs = %+v, want %+v", plan.Request.Repositories[0].Packs, wantPacks)
	}
}

func TestBuild_repoPackChoosesTheFamilyPack(t *testing.T) {
	agents, repo := fixture(t)
	write(t, agents, "packs/kaizen/rules/kaizen.md", "kaizen\n", 0o644)
	git(t, agents, "add", ".")
	git(t, agents, "commit", "-q", "-m", "kaizen")
	writeManaged(t, repo, []string{"org", "village", "kaizen"})
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := fileOf(findSet(t, plan, client.KindPack, "kaizen"), ".agents/rules/00-project-registry.md"); !ok {
		t.Error("without --repo-pack the last listed pack takes the repository's files")
	}
	for _, chosen := range []map[string]string{{"": "village"}, {"village": "village"}} {
		plan, err = Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, RepoPacks: chosen})
		if err != nil {
			t.Fatal(err)
		}
		if _, ok := fileOf(findSet(t, plan, client.KindPack, "village"), ".agents/rules/00-project-registry.md"); !ok {
			t.Errorf("--repo-pack %v did not give village the repository's files", chosen)
		}
		if _, ok := fileOf(findSet(t, plan, client.KindPack, "kaizen"), ".agents/rules/00-project-registry.md"); ok {
			t.Errorf("--repo-pack %v left the repository's files in kaizen", chosen)
		}
	}
	for name, chosen := range map[string]map[string]string{
		"org":          {"": "org"},
		"deprecated":   {"": "mdf"},
		"unknown repo": {"kagi": "village"},
	} {
		if _, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, RepoPacks: chosen}); err == nil {
			t.Errorf("%s was accepted", name)
		}
	}
}

func TestBuild_twoRepositoriesCannotShareAFamilyPack(t *testing.T) {
	agents, repo := fixture(t)
	_, other := fixture(t)
	git(t, other, "remote", "set-url", "origin", "git@github.com:senseylabs/kagi.git")
	if _, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo, other}}); err == nil || !strings.Contains(err.Error(), "--repo-pack") {
		t.Fatalf("err = %v, want the shared family pack refused", err)
	}
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo, other}, RepoPacks: map[string]string{"kagi": "kagi"}})
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := fileOf(findSet(t, plan, client.KindPack, "kagi"), ".agents/rules/00-project-registry.md"); !ok {
		t.Error("kagi lacks the files of its repository")
	}
}

func TestBuild_packParentOverridesTheDefault(t *testing.T) {
	agents, repo := fixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, Parents: map[string]string{"village": "base"}})
	if err != nil {
		t.Fatal(err)
	}
	if got := findSet(t, plan, client.KindPack, "village").Parent; got != "base" {
		t.Errorf("village parent = %q, want base", got)
	}
	if _, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, Parents: map[string]string{"kagi": "village"}}); err == nil {
		t.Error("a parent of a pack the seed does not send was accepted")
	}
}

func TestBuild_deprecatedPackIsReportedNotImported(t *testing.T) {
	agents, repo := fixture(t)
	write(t, agents, "packs/mdf/rules/mdf.md", "mdf\n", 0o644)
	write(t, agents, "packs/mdf/README.md", "mdf docs\n", 0o644)
	git(t, agents, "add", ".")
	git(t, agents, "commit", "-q", "-m", "mdf")
	write(t, repo, ".agents/rules/mdf.md", "mdf\n", 0o644)
	managed := writeManaged(t, repo, []string{"org", "mdf", "village"})
	managed.Files[".agents/rules/mdf.md"] = sum("mdf\n")
	writeManagedFile(t, repo, managed)

	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	for _, set := range plan.Request.Sets {
		if set.Name == "mdf" {
			t.Fatalf("mdf was imported: %+v", set)
		}
		if _, ok := fileOf(set, ".agents/rules/mdf.md"); ok {
			t.Errorf("the set %s holds the file of mdf", set.Name)
		}
	}
	for path, reason := range map[string]string{
		"agents:packs/mdf/":            ReasonDeprecatedPack,
		"village:packs/mdf":            ReasonDeprecatedPack,
		"village:.agents/rules/mdf.md": ReasonDeprecatedPack,
		"agents:packs/mdf/README.md":   "",
	} {
		if got := reasonOf(plan.Request.NotImported, path); got != reason {
			t.Errorf("%s reason = %q, want %q", path, got, reason)
		}
	}
	if ReasonDeprecatedPack != "Deprecated pack" {
		t.Errorf("reason = %q", ReasonDeprecatedPack)
	}
	wantPacks := []client.SeedRepositoryPack{{Name: "org"}, {Name: "village"}}
	if !reflect.DeepEqual(plan.Request.Repositories[0].Packs, wantPacks) {
		t.Errorf("packs = %+v, want mdf left out", plan.Request.Repositories[0].Packs)
	}
	if _, onDisk := plan.Repositories[0].Disk[".agents/rules/mdf.md"]; onDisk {
		t.Error("the release comparison still expects the file of mdf")
	}
	if family := plan.Repositories[0].FamilyPack; family != "village" {
		t.Errorf("family pack = %q; mdf never takes the repository's files", family)
	}
}

func TestBuild_reportsDriftAgainstManagedJSON(t *testing.T) {
	agents, repo := fixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	if len(plan.Request.Drift) != 1 {
		t.Fatalf("drift = %+v, want the edited subagent only", plan.Request.Drift)
	}
	drift := plan.Request.Drift[0]
	want := client.Drift{Path: "village:.claude/agents/coding-engineer.md", Pack: "org",
		ExpectedSHA256: sum("engineer\n"), ActualSHA256: sum("engineer, edited here\n")}
	if drift != want {
		t.Errorf("drift = %+v, want %+v", drift, want)
	}
	if len(plan.Warnings) != 0 {
		t.Errorf("warnings = %v; the managed link is intact", plan.Warnings)
	}
}

func TestBuild_missingManagedFileIsDriftWithoutActualHash(t *testing.T) {
	agents, repo := fixture(t)
	if err := os.Remove(filepath.Join(repo, ".agents", "rules", "core.md")); err != nil {
		t.Fatal(err)
	}
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	found := false
	for _, drift := range plan.Request.Drift {
		if drift.Path == "village:.agents/rules/core.md" {
			found = true
			if drift.ActualSHA256 != "" || drift.Pack != "org" {
				t.Errorf("drift = %+v", drift)
			}
		}
	}
	if !found {
		t.Errorf("a missing managed file is drift: %+v", plan.Request.Drift)
	}
}

func TestBuild_withoutAgentsSeedsOnlyPacksOutsideTheClone(t *testing.T) {
	_, repo := fixture(t)
	if _, err := Build(Options{RepoDirs: []string{repo}, SourceRef: "manual"}); err == nil || !strings.Contains(err.Error(), "--agents") {
		t.Fatalf("err = %v; the family pack village comes from the clone, so it needs --agents", err)
	}
	writeManaged(t, repo, []string{"org"})
	git(t, repo, "remote", "set-url", "origin", "git@github.com:senseylabs/acme.git")
	plan, err := Build(Options{RepoDirs: []string{repo}, SourceRef: "manual"})
	if err != nil {
		t.Fatal(err)
	}
	if len(plan.Request.Sets) != 1 || plan.Request.Sets[0].Name != "acme" || plan.Request.Sets[0].Parent != "org" {
		t.Fatalf("sets = %+v, want the repository's own pack only", plan.Request.Sets)
	}
	if plan.Request.SourceRef != "manual" {
		t.Errorf("source ref = %q", plan.Request.SourceRef)
	}
}

func TestBuild_sourceRefIsTheAgentsCommit(t *testing.T) {
	agents, repo := fixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	head, err := gitOutput(agents, "rev-parse", "HEAD")
	if err != nil {
		t.Fatal(err)
	}
	if plan.Request.SourceRef != head {
		t.Errorf("source ref = %q, want %q", plan.Request.SourceRef, head)
	}
}

func TestBuild_refusesARepositoryThatWasCutOver(t *testing.T) {
	agents, repo := fixture(t)
	if err := os.Remove(filepath.Join(repo, ".agents", ".managed.json")); err != nil {
		t.Fatal(err)
	}
	if _, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}}); !errors.Is(err, ErrCutOver) {
		t.Fatalf("err = %v, want ErrCutOver", err)
	}
	if _, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, AfterCutover: true}); err == nil || !strings.Contains(err.Error(), "--repo-pack") {
		t.Fatalf("err = %v; a cut-over repository names its family pack with --repo-pack", err)
	}
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, AfterCutover: true, RepoPacks: map[string]string{"": "village"}})
	if err != nil {
		t.Fatal(err)
	}
	if len(plan.Request.Repositories) != 0 {
		t.Errorf("a cut-over repository keeps its stored pack list: %+v", plan.Request.Repositories)
	}
	village := findSet(t, plan, client.KindPack, "village")
	if _, ok := fileOf(village, ".agents/rules/00-project-registry.md"); !ok {
		t.Error("the repository's own rule is missing")
	}
	if _, ok := fileOf(village, ".agents/rules/core.md"); ok {
		t.Error("a file org delivers unchanged was folded into village")
	}
	if _, ok := fileOf(village, ".claude/agents/coding-engineer.md"); !ok {
		t.Error("a file that differs from every pack is the repository's own")
	}
}

func TestBuild_refusesACutOverRepositoryWithoutAgents(t *testing.T) {
	_, repo := fixture(t)
	if err := os.Remove(filepath.Join(repo, ".agents", ".managed.json")); err != nil {
		t.Fatal(err)
	}
	_, err := Build(Options{RepoDirs: []string{repo}, AfterCutover: true, RepoPacks: map[string]string{"": "village"}})
	if !errors.Is(err, ErrCutOver) || !strings.Contains(err.Error(), "--agents") {
		t.Fatalf("err = %v; a cut-over repository cannot tell its own files apart without --agents", err)
	}
}

func TestBuild_oversizedFileIsNotImported(t *testing.T) {
	agents, repo := fixture(t)
	write(t, repo, ".agents/rules/big.md", string(make([]byte, MaxBlobBytes+1)), 0o644)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}})
	if err != nil {
		t.Fatal(err)
	}
	if got := reasonOf(plan.Request.NotImported, "village:.agents/rules/big.md"); got != ReasonTooLarge {
		t.Errorf("reason = %q, want %q", got, ReasonTooLarge)
	}
	if _, ok := fileOf(findSet(t, plan, client.KindPack, "village"), ".agents/rules/big.md"); ok {
		t.Errorf("an oversized file is not sent")
	}
}

func TestBuild_twoRepositoriesWithOneNameAreRefused(t *testing.T) {
	agents, repo := fixture(t)
	if _, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo, repo}}); err == nil {
		t.Fatal("two checkouts of one repository must be refused")
	}
}

func TestIsAllowedPath(t *testing.T) {
	for path, want := range map[string]bool{
		"AGENTS.md":                                        true,
		".agents/AGENTS.md":                                true,
		".agents/rules/core.md":                            true,
		".agents/skills/x/SKILL.md":                        true,
		".claude/skills/x":                                 true,
		".claude/settings.json":                            true,
		".claude/settings.local.json":                      false,
		".agents/memory/lessons.md":                        false,
		".agents/rules/../secrets":                         false,
		".agents/rules//x":                                 false,
		".claude/rules":                                    true,
		".claude/templates":                                true,
		".claude/rules/x":                                  false,
		".claude/rules/x.md":                               false,
		".claude/templates/handoff.md":                     false,
		".claude/rules/":                                   false,
		".claude/rulesx":                                   false,
		".claude/roles":                                    false,
		"projects/kagi/.claude/rules":                      false,
		"/etc/passwd":                                      false,
		`.agents/rules\x.md`:                               false,
		".agents/rules":                                    false,
		".agents/docs/README.md":                           false,
		".claude/agents/reviewer.md":                       true,
		".claude/commands/plan.md":                         false,
		".agents/workflows/review.md":                      true,
		".agents/templates/handoff.md":                     true,
		".agents/pipelines/feature.yaml":                   false,
		".agents/pipelines":                                false,
		".agents/pipelines/":                               false,
		".agents/pipelines/../x.yaml":                      false,
		".mcp.json":                                        false,
		"projects/kagi/.mcp.json":                          false,
		".claude/.mcp.json":                                false,
		".agents/.mcp.json":                                false,
		".mcp.json.bak":                                    false,
		"mcp.json":                                         false,
		"docs/private/conventions/a.md":                    true,
		"docs/private/conventions/backend/32-x.md":         true,
		"docs/private/conventions/a.png":                   false,
		"docs/private/conventions/":                        false,
		"docs/private/conventions/.md":                     false,
		"docs/private/conventions/../x.md":                 false,
		"docs/private/conventions//a.md":                   false,
		"docs/other/a.md":                                  false,
		"docs/private/a.md":                                false,
		"docs/private/conventions/backend/README.md.orig":  false,
		"projects/x/docs/private/conventions/backend/a.md": false,
		"projects/kagi/AGENTS.md":                          true,
		"a/b/c/d/AGENTS.md":                                true,
		"a/b/c/d/e/AGENTS.md":                              false,
		".x/AGENTS.md":                                     false,
		"projects/.x/AGENTS.md":                            false,
		"x/../AGENTS.md":                                   false,
		"projects/kagi/CLAUDE.md":                          false,
		"projects/kagi/README.md":                          false,
	} {
		if got := IsAllowedPath(path); got != want {
			t.Errorf("IsAllowedPath(%q) = %v, want %v", path, got, want)
		}
	}
}

func TestIsAllowedDir(t *testing.T) {
	for dir, want := range map[string]bool{
		".agents/rules":                    true,
		".agents/pipelines":                false,
		".claude/skills/x":                 true,
		"docs/private/conventions":         true,
		"docs/private/conventions/backend": true,
		"docs/private":                     false,
		".agents/memory":                   false,
		".agents/templates":                true,
		".claude/rules":                    false,
		".claude/templates":                false,
		".git":                             false,
	} {
		if got := IsAllowedDir(dir); got != want {
			t.Errorf("IsAllowedDir(%q) = %v, want %v", dir, got, want)
		}
	}
}

func TestRepositoryName(t *testing.T) {
	for remote, want := range map[string]string{
		"git@github.com:senseylabs/village.git":                   "village",
		"https://github.com/senseylabs/Village.git":               "village",
		"https://github.com/senseylabs/infra_erp/":                "infra-erp",
		"ssh://git@github.com/senseylabs/kaya":                    "kaya",
		"git@github.com:a/" + strings.Repeat("x", 59) + "-yz.git": strings.Repeat("x", 59),
	} {
		if got := RepositoryName(remote); got != want {
			t.Errorf("RepositoryName(%q) = %q, want %q", remote, got, want)
		}
	}
}

func TestCompare(t *testing.T) {
	disk := map[string]Entry{
		".agents/rules/a.md":    {Path: ".agents/rules/a.md", Mode: client.ModeFile, SHA256: sum("a")},
		".agents/hooks/h.sh":    {Path: ".agents/hooks/h.sh", Mode: client.ModeFile, SHA256: sum("h")},
		".claude/skills/k":      {Path: ".claude/skills/k", Mode: client.ModeSymlink, Target: "../../.agents/skills/other"},
		".agents/rules/only.md": {Path: ".agents/rules/only.md", Mode: client.ModeFile, SHA256: sum("o")},
		".agents/rules/edit.md": {Path: ".agents/rules/edit.md", Mode: client.ModeFile, SHA256: sum("new")},
	}
	release := client.Release{Number: 3, Files: []client.ReleaseEntry{
		{Path: ".agents/rules/a.md", Mode: client.ModeFile, SHA256: sum("a")},
		{Path: ".agents/hooks/h.sh", Mode: client.ModeExecutable, SHA256: sum("h")},
		{Path: ".claude/skills/k", Mode: client.ModeSymlink, Target: "../../.agents/skills/k"},
		{Path: ".agents/rules/edit.md", Mode: client.ModeFile, SHA256: sum("old")},
		{Path: ".agents/rules/gone.md", Mode: client.ModeFile, SHA256: sum("g")},
	}}
	got := map[string]bool{}
	for _, mismatch := range Compare(release, disk, false) {
		got[mismatch.Path] = true
	}
	want := []string{".agents/hooks/h.sh", ".claude/skills/k", ".agents/rules/edit.md", ".agents/rules/gone.md", ".agents/rules/only.md"}
	if len(got) != len(want) {
		t.Fatalf("mismatches = %v, want %v", got, want)
	}
	for _, path := range want {
		if !got[path] {
			t.Errorf("no mismatch for %s", path)
		}
	}
	if got[".agents/rules/a.md"] {
		t.Errorf("an equal path is no mismatch")
	}
}

func TestCompare_mountOverridesDifferOnlyBeforeTheCutover(t *testing.T) {
	disk := map[string]Entry{
		".agents/rules/web.md":          {Path: ".agents/rules/web.md", Mode: client.ModeFile, SHA256: sum("web, all folders")},
		".agents/rules/api.md":          {Path: ".agents/rules/api.md", Mode: client.ModeFile, SHA256: sum("api, kagi only")},
		".agents/rules/kagi/web.md":     {Path: ".agents/rules/kagi/web.md", Mode: client.ModeFile, SHA256: sum("kagi web")},
		".agents/rules/kagi/api.md":     {Path: ".agents/rules/kagi/api.md", Mode: client.ModeFile, SHA256: sum("kagi api")},
		".agents/rules/left-on-disk.md": {Path: ".agents/rules/left-on-disk.md", Mode: client.ModeFile, SHA256: sum("x")},
	}
	release := client.Release{Number: 2, Files: []client.ReleaseEntry{
		{Path: ".agents/rules/web.md", Mode: client.ModeFile, SHA256: sum("web, other folders"), NarrowedBy: "project-kagi"},
		{Path: ".agents/rules/kagi/web.md", Mode: client.ModeFile, SHA256: sum("kagi web"), OverridesPath: ".agents/rules/web.md"},
		{Path: ".agents/rules/kagi/api.md", Mode: client.ModeFile, SHA256: sum("kagi api"), OverridesPath: ".agents/rules/api.md"},
	}}

	before := map[string]bool{}
	for _, mismatch := range Compare(release, disk, false) {
		before[mismatch.Path] = true
	}
	if len(before) != 1 || !before[".agents/rules/left-on-disk.md"] {
		t.Errorf("before the cut-over the mismatches are %v, want only .agents/rules/left-on-disk.md", before)
	}

	after := map[string]bool{}
	for _, mismatch := range Compare(release, disk, true) {
		after[mismatch.Path] = true
	}
	for _, path := range []string{".agents/rules/web.md", ".agents/rules/api.md", ".agents/rules/left-on-disk.md"} {
		if !after[path] {
			t.Errorf("after the cut-over %s is no mismatch", path)
		}
	}
	if len(after) != 3 {
		t.Errorf("after the cut-over the mismatches are %v", after)
	}
}

func TestCompare_narrowedEntryStillComparesItsMode(t *testing.T) {
	disk := map[string]Entry{".agents/rules/web.md": {Path: ".agents/rules/web.md", Mode: client.ModeExecutable, SHA256: sum("a")}}
	release := client.Release{Files: []client.ReleaseEntry{
		{Path: ".agents/rules/web.md", Mode: client.ModeFile, SHA256: sum("b"), NarrowedBy: "project-kagi"},
	}}
	if got := Compare(release, disk, false); len(got) != 1 {
		t.Errorf("mismatches = %v, want the mode change", got)
	}
}

func TestScanAgents_mirrorsTheSyncExclusions(t *testing.T) {
	agents := t.TempDir()
	write(t, agents, "packs/org/hooks/dispatch.sh", "#!/bin/sh\n", 0o755)
	write(t, agents, "packs/org/hooks/README.md", "source docs", 0o644)
	write(t, agents, "packs/org/hooks/tests/guard_test.sh", "#!/bin/sh\n", 0o755)
	write(t, agents, "packs/org/hooks/__pycache__/x.pyc", "x", 0o644)
	write(t, agents, "packs/org/hooks/.DS_Store", "x", 0o644)
	write(t, agents, "packs/org/templates/.gitkeep", "", 0o644)
	write(t, agents, "packs/org/templates/sub/t.md", "t", 0o644)
	write(t, agents, "packs/org/roles/r.md", "r", 0o644)
	write(t, agents, "packs/org/roles/r.txt", "r", 0o644)
	write(t, agents, "packs/org/skills/s/SKILL.md", "s", 0o644)
	write(t, agents, "packs/org/skills/s/scripts/__pycache__/m.pyc", "x", 0o644)
	write(t, agents, "packs/org/skills/s/refs/.gitkeep", "", 0o644)
	write(t, agents, "packs/org/README.md", "pack docs", 0o644)

	scan, err := ScanAgents(agents)
	if err != nil {
		t.Fatal(err)
	}
	got := map[string]bool{}
	for path := range scan.Packs["org"].Files {
		got[path] = true
	}
	want := map[string]bool{
		".agents/hooks/dispatch.sh": true,
		".agents/roles/r.md":        true,
		".agents/skills/s/SKILL.md": true,
		".claude/skills/s":          true,
	}
	if len(got) != len(want) {
		t.Fatalf("files = %v, want %v", got, want)
	}
	for path := range want {
		if !got[path] {
			t.Errorf("missing %s; files = %v", path, got)
		}
	}
}
