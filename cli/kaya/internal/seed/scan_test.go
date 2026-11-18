package seed

import (
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/korurlabs/kaya-cli/internal/client"
)

func TestReasonFor_everyPathFamily(t *testing.T) {
	tests := []struct {
		path, target string
		symlink      bool
		want         string
	}{
		{".agents/.managed.json", "", false, ReasonManagedState},
		{".agents/.kaya-release.json", "", false, ReasonKayaState},
		{".kaya/cache/x", "", false, ReasonKayaState},
		{".claude/settings.local.json", "", false, ReasonLocalSettings},
		{"projects/kagi/.mcp.json", "", false, ReasonNotDelivered},
		{".mcp.json", "", false, ReasonNotDelivered},
		{".agents/memory/lessons.md", "", false, ReasonLessons},
		{".agents/docs/notes.md", "", false, ReasonAgentDocs},
		{".claude/roles", "../.agents/roles", true, ReasonAdapterLink},
		{"CLAUDE.md", "AGENTS.md", true, ReasonAdapterLink},
		{".claude/other", "/etc/passwd", true, ReasonLinkElsewhere},
		{".claude/other", "../docs/x", true, ReasonLinkElsewhere},
		{"docs/private/conventions/backend/diagram.png", "", false, ReasonNotConventionDoc},
		{"docs/private/conventions/link.png", "/etc/passwd", true, ReasonLinkElsewhere},
		{"CLAUDE.md", "", false, ReasonNotDelivered},
	}
	for _, tt := range tests {
		if got := reasonFor(tt.path, tt.target, tt.symlink); got != tt.want {
			t.Errorf("reasonFor(%q, %q, %v) = %q, want %q", tt.path, tt.target, tt.symlink, got, tt.want)
		}
	}
}

func TestScanRepo_splitsDeliveredFromNotImported(t *testing.T) {
	root := t.TempDir()
	write(t, root, "AGENTS.md", "root\n", 0o644)
	write(t, root, "CLAUDE.md", "claude\n", 0o644)
	write(t, root, ".mcp.json", "{}\n", 0o644)
	write(t, root, ".agents/hooks/dispatch.sh", "#!/bin/sh\n", 0o755)
	write(t, root, ".agents/memory/lessons.md", "lessons\n", 0o644)
	write(t, root, ".claude/worktrees/a/AGENTS.md", "checkout\n", 0o644)
	write(t, root, ".claude/worktrees/b/AGENTS.md", "checkout\n", 0o644)
	link(t, root, ".claude/skills/kazan", "../../.agents/skills/kazan")
	write(t, root, ".agents/rules/big.md", strings.Repeat("x", MaxBlobBytes+1), 0o644)

	scan, err := ScanRepo(root)
	if err != nil {
		t.Fatal(err)
	}

	dispatch := scan.Delivered[".agents/hooks/dispatch.sh"]
	if dispatch.Mode != client.ModeExecutable || dispatch.SHA256 != sum("#!/bin/sh\n") {
		t.Errorf("dispatch = %+v", dispatch)
	}
	skill := scan.Delivered[".claude/skills/kazan"]
	if skill.Mode != client.ModeSymlink || skill.Target != "../../.agents/skills/kazan" || skill.SHA256 != "" {
		t.Errorf("skill link = %+v", skill)
	}
	if _, ok := scan.Delivered[".mcp.json"]; ok {
		t.Error(".mcp.json is not delivered")
	}
	if scan.Delivered["AGENTS.md"].Mode != client.ModeFile {
		t.Errorf("AGENTS.md = %+v", scan.Delivered["AGENTS.md"])
	}
	if big := scan.Delivered[".agents/rules/big.md"]; big.SHA256 != "" || big.Size != MaxBlobBytes+1 {
		t.Errorf("an oversized file is listed without a hash: %+v", big)
	}
	if _, ok := scan.Delivered["CLAUDE.md"]; ok {
		t.Error("CLAUDE.md is not delivered")
	}
	want := map[string]string{
		"CLAUDE.md":                 ReasonNotDelivered,
		".agents/memory/lessons.md": ReasonLessons,
		".claude/worktrees/":        ReasonWorktrees,
	}
	for path, reason := range want {
		if got := reasonOf(scan.NotImported, path); got != reason {
			t.Errorf("reason of %s = %q, want %q", path, got, reason)
		}
	}
	worktrees := 0
	for _, item := range scan.NotImported {
		if item.Path == ".claude/worktrees/" {
			worktrees++
		}
	}
	if worktrees != 1 {
		t.Errorf("worktrees reported %d times, want once", worktrees)
	}
	for i := 1; i < len(scan.NotImported); i++ {
		if scan.NotImported[i-1].Path > scan.NotImported[i].Path {
			t.Errorf("not imported is not sorted: %v", scan.NotImported)
		}
	}
}

func TestScanRepo_deliversTheToolLinks(t *testing.T) {
	root := t.TempDir()
	write(t, root, ".agents/AGENTS.md", "agents\n", 0o644)
	write(t, root, ".agents/rules/core.md", "core\n", 0o644)
	write(t, root, ".agents/templates/handoff.md", "handoff\n", 0o644)
	link(t, root, "AGENTS.md", ".agents/AGENTS.md")
	link(t, root, ".claude/rules", "../.agents/rules")
	link(t, root, ".claude/templates", "../.agents/templates")
	link(t, root, ".claude/docs", "../.agents/docs")

	scan, err := ScanRepo(root)
	if err != nil {
		t.Fatal(err)
	}

	for path, target := range map[string]string{
		"AGENTS.md":         ".agents/AGENTS.md",
		".claude/rules":     "../.agents/rules",
		".claude/templates": "../.agents/templates",
	} {
		entry, ok := scan.Delivered[path]
		if !ok || entry.Mode != client.ModeSymlink || entry.Target != target || entry.SHA256 != "" {
			t.Errorf("%s = %+v (delivered %v), want a link to %s", path, entry, ok, target)
		}
	}
	for path := range scan.Delivered {
		if strings.HasPrefix(path, ".claude/rules/") || strings.HasPrefix(path, ".claude/templates/") {
			t.Errorf("the seed walked through a tool link: %s", path)
		}
	}
	if got := reasonOf(scan.NotImported, ".claude/docs"); got != ReasonLinkElsewhere {
		t.Errorf("reason of .claude/docs = %q, want %q", got, ReasonLinkElsewhere)
	}
}

func TestScanRepo_importsMarkdownConventionDocsOnly(t *testing.T) {
	root := t.TempDir()
	write(t, root, "docs/private/conventions/backend/32-soft-delete-convention.md", "# Soft delete\n", 0o644)
	write(t, root, "docs/private/conventions/README.md", "# Conventions\n", 0o644)
	write(t, root, "docs/private/conventions/backend/diagram.png", "png", 0o644)
	write(t, root, "docs/private/projects/kaya/notes.md", "not walked\n", 0o644)

	scan, err := ScanRepo(root)
	if err != nil {
		t.Fatal(err)
	}

	for _, path := range []string{"docs/private/conventions/backend/32-soft-delete-convention.md", "docs/private/conventions/README.md"} {
		if entry, ok := scan.Delivered[path]; !ok || entry.Mode != client.ModeFile || entry.SHA256 == "" {
			t.Errorf("%s is not delivered: %+v", path, entry)
		}
	}
	if got := reasonOf(scan.NotImported, "docs/private/conventions/backend/diagram.png"); got != ReasonNotConventionDoc {
		t.Errorf("reason of the image = %q, want %q", got, ReasonNotConventionDoc)
	}
	for _, item := range scan.NotImported {
		if strings.HasPrefix(item.Path, "docs/private/projects/") {
			t.Errorf("the seed walked outside the convention docs: %+v", item)
		}
	}
	if _, ok := scan.Delivered["docs/private/projects/kaya/notes.md"]; ok {
		t.Error("a doc outside docs/private/conventions is delivered")
	}
}

func TestScanRepo_emptyDirectoryHasNothing(t *testing.T) {
	scan, err := ScanRepo(t.TempDir())
	if err != nil || len(scan.Delivered) != 0 || len(scan.NotImported) != 0 {
		t.Fatalf("scan = %+v, %v", scan, err)
	}
}

func TestScanRepo_unreadableDirectoryFails(t *testing.T) {
	root := t.TempDir()
	write(t, root, ".agents/rules/a.md", "a\n", 0o644)
	dir := filepath.Join(root, ".agents", "rules")
	if err := os.Chmod(dir, 0o000); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = os.Chmod(dir, 0o755) }()
	if os.Geteuid() == 0 {
		t.Skip("root reads any directory")
	}
	if _, err := ScanRepo(root); err == nil {
		t.Fatal("want an error for an unreadable directory")
	}
}

func TestInPackNamespace(t *testing.T) {
	for path, want := range map[string]bool{
		".agents/rules/kagi/16-kagi-api-first.md":                       true,
		".agents/rules/kagi/notes.txt":                                  false,
		".agents/rules/16-kagi-api-first.md":                            false,
		".agents/skills/kagi-api-first/SKILL.md":                        true,
		".agents/skills/kagi/SKILL.md":                                  false,
		".agents/skills/kazan/SKILL.md":                                 false,
		".claude/skills/kagi-api-first":                                 true,
		".claude/skills/kagi-api-first/x":                               false,
		".claude/agents/kagi-reviewer.md":                               true,
		".claude/commands/kagi-release.md":                              false,
		".claude/agents/reviewer.md":                                    false,
		".agents/roles/kagi/reviewer.md":                                true,
		".agents/workflows/kagi/x.md":                                   true,
		".agents/templates/kagi/x.md":                                   true,
		".agents/hooks/kagi/x.sh":                                       false,
		"docs/private/conventions/projects/kagi/backend/32-x.md":        true,
		"docs/private/conventions/projects/kazan/backend/32-x.md":       false,
		"docs/private/conventions/backend/32-soft-delete-convention.md": false,
		"projects/kagi/AGENTS.md":                                       false,
		".claude/settings.json":                                         false,
	} {
		if got := inPackNamespace(path, "kagi"); got != want {
			t.Errorf("inPackNamespace(%q, kagi) = %v, want %v", path, got, want)
		}
	}
}

func TestParseMount(t *testing.T) {
	mount, err := ParseMount("kagi=projects/kagi/")
	if err != nil || mount != (Mount{Name: "kagi", Root: "projects/kagi"}) {
		t.Fatalf("ParseMount = %+v, %v", mount, err)
	}
	for _, value := range []string{
		"kagi", "Kagi=projects/kagi", "kagi=", "kagi=/abs", "kagi=../x", "kagi=a/b/c/d/e",
		"kagi=.agents/x", "kagi=docs/x", "kagi=projects/.hidden",
	} {
		if _, err := ParseMount(value); err == nil {
			t.Errorf("ParseMount(%q) was accepted", value)
		}
	}
}

func TestParsePackParent(t *testing.T) {
	child, parent, err := ParsePackParent(" kagi = village ")
	if err != nil || child != "kagi" || parent != "village" {
		t.Fatalf("ParsePackParent = %q, %q, %v", child, parent, err)
	}
	for _, value := range []string{"kagi", "kagi=kagi", "Kagi=village", "kagi=", "=village"} {
		if _, _, err := ParsePackParent(value); err == nil {
			t.Errorf("ParsePackParent(%q) was accepted", value)
		}
	}
}

func TestParseRepoPack(t *testing.T) {
	for value, want := range map[string][2]string{
		"village":   {"", "village"},
		"acme=acme": {"acme", "acme"},
	} {
		repository, pack, err := ParseRepoPack(value)
		if err != nil || repository != want[0] || pack != want[1] {
			t.Errorf("ParseRepoPack(%q) = %q, %q, %v", value, repository, pack, err)
		}
	}
	for _, value := range []string{"", "=village", "village=", "Village"} {
		if _, _, err := ParseRepoPack(value); err == nil {
			t.Errorf("ParseRepoPack(%q) was accepted", value)
		}
	}
}

// mountFixture adds Kagi files to the fixture repository: namespace files, the folder's
// AGENTS.md, and agent files nested below the folder that stay in git.
func mountFixture(t *testing.T) (agents, repo string) {
	t.Helper()
	agents, repo = fixture(t)
	write(t, repo, ".agents/rules/kagi/16-kagi-api-first.md", "---\npaths: [\"projects/kagi/**\"]\n---\nkagi\n", 0o644)
	write(t, repo, ".agents/skills/kagi-api-first/SKILL.md", "kagi skill\n", 0o644)
	link(t, repo, ".claude/skills/kagi-api-first", "../../.agents/skills/kagi-api-first")
	write(t, repo, "docs/private/conventions/projects/kagi/backend/32-x.md", "# Kagi doc\n", 0o644)
	write(t, repo, "projects/kagi/AGENTS.md", "kagi agents\n", 0o644)
	write(t, repo, "projects/kagi/operator/AGENTS.md", "operator\n", 0o644)
	write(t, repo, "projects/kagi/CLAUDE.md", "claude\n", 0o644)
	write(t, repo, "projects/kagi/.claude/settings.local.json", "{}\n", 0o644)
	write(t, repo, "projects/kagi/frontend/node_modules/pkg/AGENTS.md", "dependency\n", 0o644)
	write(t, repo, "projects/kagi/.next/AGENTS.md", "build output\n", 0o644)
	write(t, repo, "projects/kagi/backend/App.java", "class App {}\n", 0o644)
	return agents, repo
}

func TestBuild_mountedPackTakesItsNamespaceAndItsFolderAgentsFile(t *testing.T) {
	agents, repo := mountFixture(t)

	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, Mounts: []Mount{{Name: "kagi", Root: "projects/kagi"}}})
	if err != nil {
		t.Fatal(err)
	}

	family := findSet(t, plan, client.KindPack, "village")
	mounted := findSet(t, plan, client.KindPack, "kagi")
	if mounted.Parent != "village" {
		t.Errorf("kagi parent = %q, want the family pack village", mounted.Parent)
	}
	namespace := []string{
		".agents/rules/kagi/16-kagi-api-first.md",
		".agents/skills/kagi-api-first/SKILL.md",
		".claude/skills/kagi-api-first",
		"docs/private/conventions/projects/kagi/backend/32-x.md",
	}
	for _, path := range namespace {
		if _, ok := fileOf(mounted, path); !ok {
			t.Errorf("the mounted pack lacks %s", path)
		}
		if _, ok := fileOf(family, path); ok {
			t.Errorf("the family pack still holds %s", path)
		}
	}
	agentsFile, ok := fileOf(mounted, "AGENTS.md")
	if !ok || agentsFile.SHA256 != sum("kagi agents\n") {
		t.Errorf("the folder AGENTS.md is sent as %+v, want AGENTS.md with its bytes", agentsFile)
	}
	if _, ok := fileOf(mounted, "projects/kagi/AGENTS.md"); ok {
		t.Error("the folder AGENTS.md is sent at its folder path")
	}
	if len(mounted.Files) != len(namespace)+1 {
		t.Errorf("mounted files = %+v", mounted.Files)
	}
	if root, _ := fileOf(family, "AGENTS.md"); root.SymlinkTarget != ".agents/AGENTS.md" {
		t.Errorf("the family pack's AGENTS.md = %+v, want the root link", root)
	}
	if _, ok := plan.Blobs[sum("kagi agents\n")]; !ok {
		t.Error("the folder's AGENTS.md has no blob")
	}
	if _, ok := plan.Repositories[0].Disk["projects/kagi/AGENTS.md"]; !ok {
		t.Error("the release comparison does not expect projects/kagi/AGENTS.md")
	}
	wantPacks := []client.SeedRepositoryPack{{Name: "org"}, {Name: "village"}, {Name: "kagi", RootPath: "projects/kagi"}}
	if !reflect.DeepEqual(plan.Request.Repositories[0].Packs, wantPacks) {
		t.Errorf("packs = %+v, want %+v", plan.Request.Repositories[0].Packs, wantPacks)
	}
	for _, path := range []string{"projects/kagi/operator/AGENTS.md", "projects/kagi/CLAUDE.md", "projects/kagi/.claude/settings.local.json"} {
		if got := reasonOf(plan.Request.NotImported, "village:"+path); got != ReasonNestedAgentFiles {
			t.Errorf("%s: reason %q", path, got)
		}
	}
	for _, path := range []string{"projects/kagi/frontend/node_modules/pkg/AGENTS.md", "projects/kagi/.next/AGENTS.md", "projects/kagi/backend/App.java"} {
		if got := reasonOf(plan.Request.NotImported, "village:"+path); got != "" {
			t.Errorf("%s is reported: %q", path, got)
		}
	}
}

func TestBuild_mountedPackOfTheCloneKeepsItsFilesAndLeavesTheRoot(t *testing.T) {
	agents, repo := mountFixture(t)
	write(t, agents, "packs/kagi/skills/kagi-shared/SKILL.md", "shared\n", 0o644)
	git(t, agents, "add", ".")
	git(t, agents, "commit", "-q", "-m", "kagi")
	writeManaged(t, repo, []string{"org", "village", "kagi"})

	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, RepoPacks: map[string]string{"": "village"},
		Mounts: []Mount{{Name: "kagi", Root: "projects/kagi"}}, Parents: map[string]string{"kagi": "village"}})
	if err != nil {
		t.Fatal(err)
	}
	mounted := findSet(t, plan, client.KindPack, "kagi")
	if mounted.Parent != "village" {
		t.Errorf("kagi parent = %q", mounted.Parent)
	}
	for _, path := range []string{".agents/skills/kagi-shared/SKILL.md", ".agents/rules/kagi/16-kagi-api-first.md", "AGENTS.md"} {
		if _, ok := fileOf(mounted, path); !ok {
			t.Errorf("kagi lacks %s", path)
		}
	}
	wantPacks := []client.SeedRepositoryPack{{Name: "org"}, {Name: "village"}, {Name: "kagi", RootPath: "projects/kagi"}}
	if !reflect.DeepEqual(plan.Request.Repositories[0].Packs, wantPacks) {
		t.Errorf("packs = %+v; a mounted pack is not used at the root too", plan.Request.Repositories[0].Packs)
	}
}

func TestBuild_refusesBadMounts(t *testing.T) {
	agents, repo := mountFixture(t)
	build := func(mounts []Mount) (Plan, error) {
		return Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, Mounts: mounts})
	}
	for name, mounts := range map[string][]Mount{
		"nested folders":   {{Name: "kagi", Root: "projects/kagi"}, {Name: "operator", Root: "projects/kagi/operator"}},
		"one name twice":   {{Name: "kagi", Root: "projects/kagi"}, {Name: "kagi", Root: "projects/kazan"}},
		"the family pack":  {{Name: "village", Root: "projects/kagi"}},
		"the root pack":    {{Name: "org", Root: "projects/kagi"}},
		"deprecated pack":  {{Name: "mdf", Root: "projects/kagi"}},
		"missing folder":   {{Name: "kazan", Root: "projects/kazan"}},
		"a file as folder": {{Name: "app", Root: "projects/kagi/backend/App.java"}},
	} {
		if _, err := build(mounts); err == nil {
			t.Errorf("%s was accepted", name)
		}
	}
	plan, err := build(nil)
	if err != nil {
		t.Fatal(err)
	}
	for _, pack := range plan.Request.Repositories[0].Packs {
		if pack.RootPath != "" {
			t.Errorf("a plan without mounts mounts %s", pack.Name)
		}
	}
}

func TestPrintPlan_namesParentsAndMounts(t *testing.T) {
	agents, repo := mountFixture(t)
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, Mounts: []Mount{{Name: "kagi", Root: "projects/kagi"}}})
	if err != nil {
		t.Fatal(err)
	}

	var out strings.Builder
	PrintPlan(&out, plan)

	mounted := findSet(t, plan, client.KindPack, "kagi")
	for _, want := range []string{
		fmt.Sprintf("  PACK      kagi                        %d files  parent village\n", len(mounted.Files)),
		"  village  remote https://github.com/senseylabs/Village.git  packs [org, village, kagi at projects/kagi]\n",
	} {
		if !strings.Contains(out.String(), want) {
			t.Errorf("the plan lacks %q:\n%s", want, out.String())
		}
	}
}
