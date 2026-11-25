package upload

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestFindProjectReadsFileInDirectory(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, ProjectFileName), "organization: org-1\nupload:\n  - dir: docs\n    to: engineering/docs\n    lang: TR\n")
	p, err := FindProject(dir)
	if err != nil || p == nil {
		t.Fatalf("FindProject() = %v, %v", p, err)
	}
	if p.Organization != "org-1" || len(p.Upload) != 1 || p.Upload[0].To != "engineering/docs" {
		t.Fatalf("parsed %+v", p)
	}
	if got := p.DirOf(p.Upload[0]); got != filepath.Join(dir, "docs") {
		t.Fatalf("DirOf = %s", got)
	}
}

func TestFindProjectFallsBackToGitRoot(t *testing.T) {
	root := t.TempDir()
	writeFile(t, filepath.Join(root, ".git"), "gitdir: elsewhere\n") // a linked worktree has a .git file
	writeFile(t, filepath.Join(root, ProjectFileName), "organization: org-root\n")
	sub := filepath.Join(root, "a", "b")
	if err := os.MkdirAll(sub, 0o750); err != nil {
		t.Fatal(err)
	}
	p, err := FindProject(sub)
	if err != nil || p == nil || p.Organization != "org-root" {
		t.Fatalf("FindProject() = %+v, %v", p, err)
	}
}

func TestFindProjectPrefersCurrentDirectoryOverGitRoot(t *testing.T) {
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(root, ProjectFileName), "organization: org-root\n")
	sub := filepath.Join(root, "sub")
	writeFile(t, filepath.Join(sub, ProjectFileName), "organization: org-sub\n")
	p, err := FindProject(sub)
	if err != nil || p == nil || p.Organization != "org-sub" {
		t.Fatalf("FindProject() = %+v, %v", p, err)
	}
}

func TestFindProjectReturnsNilWithoutFile(t *testing.T) {
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	p, err := FindProject(root)
	if err != nil || p != nil {
		t.Fatalf("FindProject() = %+v, %v; want nil, nil", p, err)
	}
}

func TestFindProjectRejectsInvalidFiles(t *testing.T) {
	cases := map[string]struct{ content, want string }{
		"unknown key": {"organizaton: x\n", "organizaton"},
		"bad yaml":    {"upload: [\n", "kalkan.yaml"},
		"no dir":      {"upload:\n  - to: a\n", "no dir"},
		"no to":       {"upload:\n  - dir: docs\n", "no to"},
		"bad lang":    {"upload:\n  - dir: docs\n    to: a\n    lang: de\n", "use tr or en"},
		"push key":    {"push:\n  - dir: docs\n    to: a\n", "field push not found"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			dir := t.TempDir()
			writeFile(t, filepath.Join(dir, ProjectFileName), tc.content)
			p, err := FindProject(dir)
			if err == nil || p != nil {
				t.Fatalf("FindProject() = %+v, %v; want an error", p, err)
			}
			if !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("error %q lacks %q", err, tc.want)
			}
		})
	}
}

func TestFindProjectAcceptsEmptyFile(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, ProjectFileName), "")
	p, err := FindProject(dir)
	if err != nil || p == nil || p.Organization != "" || len(p.Upload) != 0 {
		t.Fatalf("FindProject() = %+v, %v", p, err)
	}
}

func TestDirOfKeepsAbsolutePaths(t *testing.T) {
	p := &Project{Dir: "/repo"}
	if got := p.DirOf(Mapping{Dir: "/abs/../docs"}); got != "/docs" {
		t.Fatalf("DirOf = %s", got)
	}
}
