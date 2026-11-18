package project

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func write(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestFindReadsFileInDirectory(t *testing.T) {
	dir := t.TempDir()
	write(t, filepath.Join(dir, FileName), "organization: org-1\npush:\n  - dir: docs\n    to: engineering/docs\n    lang: TR\n")
	p, err := Find(dir)
	if err != nil || p == nil {
		t.Fatalf("Find() = %v, %v", p, err)
	}
	if p.Organization != "org-1" || len(p.Push) != 1 || p.Push[0].To != "engineering/docs" {
		t.Fatalf("parsed %+v", p)
	}
	if got := p.DirOf(p.Push[0]); got != filepath.Join(dir, "docs") {
		t.Fatalf("DirOf = %s", got)
	}
}

func TestFindFallsBackToGitRoot(t *testing.T) {
	root := t.TempDir()
	write(t, filepath.Join(root, ".git"), "gitdir: elsewhere\n") // a linked worktree has a .git file
	write(t, filepath.Join(root, FileName), "organization: org-root\n")
	sub := filepath.Join(root, "a", "b")
	if err := os.MkdirAll(sub, 0o750); err != nil {
		t.Fatal(err)
	}
	p, err := Find(sub)
	if err != nil || p == nil || p.Organization != "org-root" {
		t.Fatalf("Find() = %+v, %v", p, err)
	}
}

func TestFindPrefersCurrentDirectoryOverGitRoot(t *testing.T) {
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	write(t, filepath.Join(root, FileName), "organization: org-root\n")
	sub := filepath.Join(root, "sub")
	write(t, filepath.Join(sub, FileName), "organization: org-sub\n")
	p, err := Find(sub)
	if err != nil || p == nil || p.Organization != "org-sub" {
		t.Fatalf("Find() = %+v, %v", p, err)
	}
}

func TestFindReturnsNilWithoutFile(t *testing.T) {
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	p, err := Find(root)
	if err != nil || p != nil {
		t.Fatalf("Find() = %+v, %v; want nil, nil", p, err)
	}
}

func TestFindRejectsInvalidFiles(t *testing.T) {
	cases := map[string]struct{ content, want string }{
		"unknown key": {"organizaton: x\n", "organizaton"},
		"bad yaml":    {"push: [\n", "kalkan.yaml"},
		"no dir":      {"push:\n  - to: a\n", "no dir"},
		"no to":       {"push:\n  - dir: docs\n", "no to"},
		"bad lang":    {"push:\n  - dir: docs\n    to: a\n    lang: de\n", "use tr or en"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			dir := t.TempDir()
			write(t, filepath.Join(dir, FileName), tc.content)
			p, err := Find(dir)
			if err == nil || p != nil {
				t.Fatalf("Find() = %+v, %v; want an error", p, err)
			}
			if !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("error %q lacks %q", err, tc.want)
			}
		})
	}
}

func TestFindAcceptsEmptyFile(t *testing.T) {
	dir := t.TempDir()
	write(t, filepath.Join(dir, FileName), "")
	p, err := Find(dir)
	if err != nil || p == nil || p.Organization != "" || len(p.Push) != 0 {
		t.Fatalf("Find() = %+v, %v", p, err)
	}
}

func TestDirOfKeepsAbsolutePaths(t *testing.T) {
	p := &Project{Dir: "/repo"}
	if got := p.DirOf(Mapping{Dir: "/abs/../docs"}); got != "/docs" {
		t.Fatalf("DirOf = %s", got)
	}
}
