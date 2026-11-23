package delivery

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"testing"
	"time"
)

// gitOutput runs git in dir and returns what it printed.
func gitOutput(t *testing.T, dir string, args ...string) string {
	t.Helper()
	out, err := exec.Command("git", append([]string{"-C", dir}, args...)...).CombinedOutput()
	if err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, out)
	}
	return string(out)
}

// commit writes files into the checkout and commits exactly them.
func (f *fixture) commit(files map[string]string) {
	f.t.Helper()
	for path, content := range files {
		abs := filepath.Join(f.root, filepath.FromSlash(path))
		if err := os.MkdirAll(filepath.Dir(abs), 0o750); err != nil {
			f.t.Fatal(err)
		}
		if err := os.WriteFile(abs, []byte(content), 0o600); err != nil {
			f.t.Fatal(err)
		}
		gitIn(f.t, f.root, "add", "--", path)
	}
	gitIn(f.t, f.root, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "files")
}

// worktree adds a linked worktree of the checkout on a new branch.
func (f *fixture) worktree() string {
	f.t.Helper()
	dir := filepath.Join(f.t.TempDir(), "linked")
	gitIn(f.t, f.root, "worktree", "add", "-q", "-b", "linked", dir)
	return dir
}

// deliverer syncs another checkout of the same repository with the fixture's server, keys and
// blob cache.
func (f *fixture) deliverer(dir string) *Deliverer {
	f.t.Helper()
	checkout := mustFind(f.t, dir)
	if checkout.Kind != Delivered {
		f.t.Fatalf("kind of %s = %s, want Delivered", dir, checkout.Kind)
	}
	return &Deliverer{Checkout: checkout, Source: f.source, Keys: f.keys, Cache: f.d.Cache, OnlineTimeout: 5 * time.Second}
}

func (f *fixture) excludePath() string {
	return filepath.Join(f.root, ".git", "info", "exclude")
}

// excludeBlock is the entries of Kaya's block in info/exclude.
func (f *fixture) excludeBlock() []string {
	f.t.Helper()
	data, err := os.ReadFile(f.excludePath())
	if err != nil {
		f.t.Fatal(err)
	}
	var entries []string
	inBlock := false
	for _, line := range strings.Split(string(data), "\n") {
		switch {
		case line == ExcludeBegin:
			inBlock = true
		case line == ExcludeEnd:
			inBlock = false
		case inBlock && line != "" && !strings.HasPrefix(line, "#"):
			entries = append(entries, line)
		}
	}
	return entries
}

// assertGitSeesNothing fails when git status or git add would show anything in dir.
func assertGitSeesNothing(t *testing.T, dir string) {
	t.Helper()
	if status := gitOutput(t, dir, "status", "--porcelain"); status != "" {
		t.Fatalf("git status --porcelain in %s:\n%s", dir, status)
	}
	if added := gitOutput(t, dir, "add", "-A", "--dry-run"); added != "" {
		t.Fatalf("git add -A --dry-run in %s:\n%s", dir, added)
	}
}

func filesWith(agents string, extra ...spec) []spec {
	return append(baseFiles(agents), extra...)
}

func TestSync_writesOneAnchoredExcludeBlockAndGitSeesNothing(t *testing.T) {
	f := newFixture(t)
	before, err := os.ReadFile(f.excludePath())
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		t.Fatal(err)
	}
	userLines := string(before) + "*.log\n"
	if err := os.WriteFile(f.excludePath(), []byte(userLines), 0o600); err != nil {
		t.Fatal(err)
	}
	f.release(1, filesWith("v1\n",
		spec{path: "docs/private/conventions/backend/style.md", content: "style\n"},
		spec{path: "services/api/AGENTS.md", content: "api\n"}))

	out := f.sync(Pin)

	if out.Active != 1 || len(out.Warnings) > 0 {
		t.Fatalf("outcome = %+v", out)
	}
	want := []string{"/.agents", "/.claude", "/.kaya", "/AGENTS.md", "/docs/private/conventions/backend/style.md", "/services/api/AGENTS.md"}
	if got := f.excludeBlock(); !slices.Equal(got, want) {
		t.Fatalf("exclude block = %q, want %q", got, want)
	}
	written, err := os.ReadFile(f.excludePath())
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(string(written), userLines) {
		t.Fatalf("the lines before the block changed:\n%s", written)
	}
	if strings.Count(string(written), ExcludeBegin) != 1 || strings.Count(string(written), ExcludeEnd) != 1 {
		t.Fatalf("want one block:\n%s", written)
	}
	for _, path := range []string{".gitignore", "services/api/.gitignore", "docs/.gitignore"} {
		if _, err := os.Lstat(filepath.Join(f.root, path)); !errors.Is(err, os.ErrNotExist) {
			t.Fatalf("%s was written (err %v)", path, err)
		}
	}
	assertGitSeesNothing(t, f.root)

	f.sync(Pin)

	again, err := os.ReadFile(f.excludePath())
	if err != nil {
		t.Fatal(err)
	}
	if string(again) != string(written) {
		t.Fatalf("a second sync changed info/exclude:\n%s", again)
	}
}

func TestSync_aLaterReleaseAddsExcludeEntriesAndRemovesNone(t *testing.T) {
	f := newFixture(t)
	f.release(1, filesWith("v1\n", spec{path: "services/api/AGENTS.md", content: "api\n"}))
	f.sync(Pin)
	f.release(2, filesWith("v2\n", spec{path: "docs/private/conventions/team.md", content: "team\n"}))

	out := f.sync(Pin)

	if out.Active != 2 {
		t.Fatalf("outcome = %+v", out)
	}
	want := []string{"/.agents", "/.claude", "/.kaya", "/AGENTS.md", "/docs/private/conventions/team.md", "/services/api/AGENTS.md"}
	if got := f.excludeBlock(); !slices.Equal(got, want) {
		t.Fatalf("exclude block = %q, want %q", got, want)
	}
	assertGitSeesNothing(t, f.root)
}

func TestSync_aLinkedWorktreeReadsTheBlockOfTheSharedGitDirectory(t *testing.T) {
	f := newFixture(t)
	f.commit(map[string]string{"README.md": "demo\n"})
	dir := f.worktree()
	f.release(1, filesWith("v1\n", spec{path: "services/api/AGENTS.md", content: "api\n"}))
	d := f.deliverer(dir)

	out, err := d.Sync(context.Background(), Pin)

	if err != nil || out.Active != 1 {
		t.Fatalf("outcome = %+v, err = %v", out, err)
	}
	if data, err := os.ReadFile(filepath.Join(dir, "services", "api", "AGENTS.md")); err != nil || string(data) != "api\n" {
		t.Fatalf("services/api/AGENTS.md = %q, %v", data, err)
	}
	if got := f.excludeBlock(); !slices.Contains(got, "/services/api/AGENTS.md") {
		t.Fatalf("the main checkout's info/exclude holds %q", got)
	}
	if _, err := os.Lstat(filepath.Join(f.root, ".git", "worktrees", "linked", "info", "exclude")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("the worktree has an exclude file of its own (err %v)", err)
	}
	assertGitSeesNothing(t, dir)
	assertGitSeesNothing(t, f.root)
}

func TestWriteExcludes_twoWorktreesWritingAtOnceKeepEveryEntry(t *testing.T) {
	f := newFixture(t)
	f.commit(map[string]string{"README.md": "demo\n"})
	checkouts := []*Checkout{mustFind(t, f.root), mustFind(t, f.worktree())}
	var want []string
	for round := range 40 {
		manifests := make([]Manifest, len(checkouts))
		for i := range checkouts {
			path := fmt.Sprintf("docs/round-%d/checkout-%d.md", round, i)
			manifests[i] = Manifest{Files: []ManifestFile{{Path: path, Mode: "FILE"}}}
			want = append(want, "/"+path)
		}
		start := make(chan struct{})
		errs := make([]error, len(checkouts))
		var wg sync.WaitGroup
		for i, checkout := range checkouts {
			wg.Add(1)
			go func() {
				defer wg.Done()
				<-start
				errs[i] = checkout.WriteExcludes(manifests[i])
			}()
		}
		close(start)
		wg.Wait()
		if err := errors.Join(errs...); err != nil {
			t.Fatal(err)
		}
		got := f.excludeBlock()
		for _, entry := range want {
			if !slices.Contains(got, entry) {
				t.Fatalf("after round %d the block lacks %s:\n%s", round, entry, strings.Join(got, "\n"))
			}
		}
	}
}

func TestSync_restoresDeliveryAfterGitCleanFromTheBlobCache(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	blobs := f.source.blobCalls
	gitIn(t, f.root, "clean", "-fdxq")
	for _, path := range []string{"AGENTS.md", ".kaya", ".claude/settings.json"} {
		if _, err := os.Lstat(filepath.Join(f.root, path)); !errors.Is(err, os.ErrNotExist) {
			t.Fatalf("git clean -fdx left %s (err %v)", path, err)
		}
	}

	out := f.sync(Pin)

	if out.Active != 1 || out.State != StateFresh {
		t.Fatalf("outcome = %+v", out)
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if f.source.blobCalls != blobs {
		t.Fatalf("blob downloads = %d, want %d: the cache holds every blob", f.source.blobCalls, blobs)
	}
	assertGitSeesNothing(t, f.root)
}

func TestSync_refusesAReleaseOverATrackedPathBeforeChangingAnyFile(t *testing.T) {
	f := newFixture(t)
	f.commit(map[string]string{"AGENTS.md": "mine\n", "docs/private/conventions/team.md": "ours\n"})
	f.release(1, filesWith("v1\n", spec{path: "docs/private/conventions/team.md", content: "team\n"}))

	out, err := f.d.Sync(context.Background(), Pin)

	var tracked *TrackedError
	if !errors.As(err, &tracked) {
		t.Fatalf("err = %v, want a TrackedError", err)
	}
	if want := []string{"AGENTS.md", "docs/private/conventions/team.md"}; !slices.Equal(tracked.Paths, want) {
		t.Fatalf("tracked paths = %q, want %q", tracked.Paths, want)
	}
	if out.Active != 0 || out.State != StateNone {
		t.Fatalf("outcome = %+v", out)
	}
	for _, check := range []struct{ path, content string }{{"AGENTS.md", "mine\n"}, {"docs/private/conventions/team.md", "ours\n"}} {
		info, err := os.Lstat(filepath.Join(f.root, check.path))
		if err != nil || !info.Mode().IsRegular() {
			t.Fatalf("%s is no longer the regular file (err %v)", check.path, err)
		}
		if got := f.read(check.path); got != check.content {
			t.Fatalf("%s = %q, want %q", check.path, got, check.content)
		}
	}
	for _, path := range []string{".claude", ".agents", ".kaya/current"} {
		if _, err := os.Lstat(filepath.Join(f.root, path)); !errors.Is(err, os.ErrNotExist) {
			t.Fatalf("%s was written (err %v)", path, err)
		}
	}
	if got := f.excludeBlock(); len(got) != 0 {
		t.Fatalf("exclude block = %q, want none", got)
	}
	if status := gitOutput(t, f.root, "status", "--porcelain", "--untracked-files=no"); status != "" {
		t.Fatalf("tracked files changed:\n%s", status)
	}

	// Each printed command untracks its file and keeps it; the next sync then places the release.
	var commands int
	for _, line := range strings.Split(err.Error(), "\n") {
		path, ok := strings.CutPrefix(strings.TrimSpace(line), "git rm --cached -- ")
		if !ok {
			continue
		}
		commands++
		gitIn(t, f.root, "rm", "--cached", "-q", "--", path)
	}
	if commands != 2 {
		t.Fatalf("the error names %d commands, want 2:\n%v", commands, err)
	}
	if got := f.read("AGENTS.md"); got != "mine\n" {
		t.Fatalf("git rm --cached changed AGENTS.md to %q", got)
	}
	if out := f.sync(Pin); out.Active != 1 || f.read("AGENTS.md") != "v1\n" {
		t.Fatalf("after untracking: outcome = %+v, AGENTS.md = %q", out, f.read("AGENTS.md"))
	}
}

func TestSync_aTrackedPathInANewerReleaseKeepsTheActiveOnePlaced(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	f.commit(map[string]string{"docs/private/conventions/team.md": "ours\n"})
	f.release(2, filesWith("v2\n", spec{path: "docs/private/conventions/team.md", content: "team\n"}))

	out, err := f.d.Sync(context.Background(), Pin)

	var tracked *TrackedError
	if !errors.As(err, &tracked) || !slices.Equal(tracked.Paths, []string{"docs/private/conventions/team.md"}) {
		t.Fatalf("err = %v, want a TrackedError naming docs/private/conventions/team.md", err)
	}
	if !strings.Contains(err.Error(), "git rm --cached -- docs/private/conventions/team.md") {
		t.Fatalf("err = %v", err)
	}
	if out.Active != 1 || out.Switched || len(out.Warnings) > 0 {
		t.Fatalf("outcome = %+v", out)
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if got := f.read("docs/private/conventions/team.md"); got != "ours\n" {
		t.Fatalf("team.md = %q", got)
	}
	if target, err := os.Readlink(filepath.Join(f.root, ".kaya", "current")); err != nil || target != filepath.Join("versions", "1") {
		t.Fatalf(".kaya/current = %q, %v", target, err)
	}
	state, err := f.d.Checkout.LoadState()
	if err != nil || state.Active != 1 || state.Latest != 2 {
		t.Fatalf("state = %+v, %v", state, err)
	}
}

func TestMergeExcludeBlock(t *testing.T) {
	block := func(entries ...string) string {
		return ExcludeBegin + "\n" + excludeNote + "\n" + strings.Join(entries, "\n") + "\n" + ExcludeEnd + "\n"
	}
	tests := []struct {
		name, content string
		entries       []string
		want          string
	}{
		{"empty file", "", []string{"/.agents", "/.claude", "/.kaya"}, block("/.agents", "/.claude", "/.kaya")},
		{"a last line without a newline", "*.log", []string{"/.kaya"}, "*.log\n" + block("/.kaya")},
		{
			"earlier entries are kept and the block stays in place",
			"# git\n" + ExcludeBegin + "\n/.kaya\n/services/api/AGENTS.md\n" + ExcludeEnd + "\n*.tmp\n",
			[]string{"/.agents", "/AGENTS.md"},
			"# git\n" + block("/.agents", "/.kaya", "/AGENTS.md", "/services/api/AGENTS.md") + "*.tmp\n",
		},
		{
			"repeated blocks become one",
			ExcludeBegin + "\n/a\n" + ExcludeEnd + "\nkeep\n" + ExcludeBegin + "\n/b\n" + ExcludeEnd + "\n",
			nil,
			block("/a", "/b") + "keep\n",
		},
		{"lines outside are kept byte for byte", "a\r\n\r\nb  \n", []string{"/.kaya"}, "a\r\n\r\nb  \n" + block("/.kaya")},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := MergeExcludeBlock([]byte(tt.content), tt.entries)
			if err != nil {
				t.Fatal(err)
			}
			if string(got) != tt.want {
				t.Fatalf("got:\n%s\nwant:\n%s", got, tt.want)
			}
			again, err := MergeExcludeBlock(got, tt.entries)
			if err != nil || string(again) != string(got) {
				t.Fatalf("a second merge changed the file (err %v):\n%s", err, again)
			}
		})
	}
}

func TestMergeExcludeBlock_refusesABrokenBlock(t *testing.T) {
	for _, content := range []string{
		ExcludeBegin + "\n/a\n",
		ExcludeBegin + "\n/a\n" + ExcludeBegin + "\n" + ExcludeEnd + "\n",
	} {
		if _, err := MergeExcludeBlock([]byte(content), []string{"/.kaya"}); err == nil {
			t.Fatalf("no error for %q", content)
		}
	}
}

func TestExcludeEntries_anchorEachPathAndMatchOnlyItself(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t)
	manifest := Manifest{Files: []ManifestFile{
		{Path: ".agents/rules/core.md"},
		{Path: ".claude/settings.json"},
		{Path: "docs/private/conventions/a*b?[c].md"},
		{Path: "services/api/AGENTS.md"},
	}}

	entries, err := ExcludeEntries(manifest)

	if err != nil {
		t.Fatal(err)
	}
	want := []string{"/.agents", "/.claude", "/.kaya", `/docs/private/conventions/a\*b\?\[c].md`, "/services/api/AGENTS.md"}
	if !slices.Equal(entries, want) {
		t.Fatalf("entries = %q, want %q", entries, want)
	}
	for _, entry := range entries {
		if strings.HasSuffix(entry, "/") {
			t.Fatalf("%q ends with a slash", entry)
		}
	}
	content, err := MergeExcludeBlock(nil, entries)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".git", "info", "exclude"), content, 0o600); err != nil {
		t.Fatal(err)
	}
	for path, content := range map[string]string{
		"docs/private/conventions/a*b?[c].md": "x", "docs/private/conventions/axbyc.md": "x",
		"services/api/AGENTS.md": "x", "other/services/api/AGENTS.md": "x",
	} {
		abs := filepath.Join(root, filepath.FromSlash(path))
		if err := os.MkdirAll(filepath.Dir(abs), 0o750); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(abs, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	got := strings.Split(strings.TrimSpace(gitOutput(t, root, "-c", "core.quotePath=false", "ls-files", "--others", "--exclude-standard")), "\n")
	if want := []string{"docs/private/conventions/axbyc.md", "other/services/api/AGENTS.md"}; !slices.Equal(got, want) {
		t.Fatalf("untracked files git sees = %q, want %q", got, want)
	}
	if pattern, err := excludePattern("a "); err != nil || pattern != `/a\ ` {
		t.Fatalf("pattern of %q = %q, %v", "a ", pattern, err)
	}
	if _, err := excludePattern("a\nb"); err == nil {
		t.Fatal("a path with a line break was accepted")
	}
}
