package githook

import (
	"bytes"
	"context"
	"errors"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/install"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	agentsContent = "# Rules\n"
	skillContent  = "---\nname: demo\n---\n"
	nullSHA       = "0000000000000000000000000000000000000000"
)

// delivered sets up a checkout with one commit and release 1 of Kaya in place, and returns the
// fake Kaya and the checkout's root, which is the working directory.
func delivered(t *testing.T) (*kayatest.FakeKaya, string) {
	t.Helper()
	kaya, root := kayatest.SetupDelivery(t)
	for key, value := range map[string]string{
		"GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "test@example.com",
		"GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "test@example.com",
	} {
		t.Setenv(key, value)
	}
	writeFile(t, filepath.Join(root, "README.md"), "demo\n")
	kayatest.Git(t, root, "add", "README.md")
	kayatest.Git(t, root, "commit", "-q", "-m", "first")
	kayatest.Git(t, root, "branch", "-M", "main")
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": agentsContent, ".agents/skills/demo/SKILL.md": skillContent})
	if out, err := kayatest.Sync(t); err != nil {
		t.Fatalf("sync: %v\n%s", err, out)
	}
	return kaya, root
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func gitOutput(t *testing.T, dir string, args ...string) string {
	t.Helper()
	out, err := exec.Command("git", append([]string{"-C", dir}, args...)...).CombinedOutput()
	if err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, out)
	}
	return strings.TrimSpace(string(out))
}

func chdir(t *testing.T, dir string) {
	t.Helper()
	wd, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chdir(dir); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.Chdir(wd) })
}

func run(t *testing.T, event string, args ...string) (int, string) {
	t.Helper()
	var stderr bytes.Buffer
	code := Run(context.Background(), event, args, &stderr)
	return code, stderr.String()
}

func readContent(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(data)
}

// addWorktree adds a linked worktree of root on a new branch. git runs no hook of Kaya here,
// since the test's global git config is empty.
func addWorktree(t *testing.T, root, name string) string {
	t.Helper()
	dir := filepath.Join(t.TempDir(), name)
	kayatest.Git(t, root, "worktree", "add", "-q", "-b", name, dir)
	return dir
}

// realPath is path with its links resolved, as git names a worktree.
func realPath(t *testing.T, path string) string {
	t.Helper()
	resolved, err := filepath.EvalSymlinks(path)
	if err != nil {
		t.Fatal(err)
	}
	return resolved
}

func loadState(t *testing.T, root string) delivery.State {
	t.Helper()
	state, err := (&delivery.Checkout{Root: root}).LoadState()
	if err != nil {
		t.Fatal(err)
	}
	return state
}

// unreachable points the machine calls at a server that no longer listens.
func unreachable(t *testing.T) {
	t.Helper()
	server := httptest.NewServer(nil)
	server.Close()
	t.Setenv(config.EnvAPIURL, server.URL)
}

func TestEventsAreTheOnesInstallRegisters(t *testing.T) {
	if !slices.Equal(Events, install.GitHookEvents) {
		t.Fatalf("git-hook handles %v, install registers %v", Events, install.GitHookEvents)
	}
}

func TestPreCommit_refusesAForceAddedDeliveredPathAndNamesTheFix(t *testing.T) {
	_, root := delivered(t)
	kayatest.Git(t, root, "add", "-f", "AGENTS.md", ".agents/skills/demo/SKILL.md")

	code, stderr := run(t, PreCommit)
	if code != 1 {
		t.Fatalf("exit %d, want 1; stderr:\n%s", code, stderr)
	}
	for _, want := range []string{
		"  AGENTS.md\n", "  .agents/skills/demo/SKILL.md\n",
		"git rm -r --cached -- AGENTS.md\n", "git rm -r --cached -- .agents/skills/demo/SKILL.md\n",
	} {
		if !strings.Contains(stderr, want) {
			t.Fatalf("stderr lacks %q:\n%s", want, stderr)
		}
	}

	kayatest.Git(t, root, "rm", "-q", "-r", "--cached", "--", "AGENTS.md")
	kayatest.Git(t, root, "rm", "-q", "-r", "--cached", "--", ".agents/skills/demo/SKILL.md")
	if code, stderr := run(t, PreCommit); code != 0 || stderr != "" {
		t.Fatalf("after the named fix: exit %d, stderr %q", code, stderr)
	}
	if _, err := os.Stat(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatalf("the fix removed the delivered file: %v", err)
	}
}

func TestPreCommit_refusesAStagedTypeChangeOfADeliveredPath(t *testing.T) {
	_, root := delivered(t)
	// A commit made without the hook tracks Kaya's link; replacing it by a file is a type change.
	kayatest.Git(t, root, "add", "-f", "AGENTS.md")
	kayatest.Git(t, root, "commit", "-q", "-m", "track the link")
	if err := os.Remove(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(root, "AGENTS.md"), "# Edited by hand\n")
	kayatest.Git(t, root, "add", "AGENTS.md")
	if status := gitOutput(t, root, "diff", "--cached", "--name-status"); status != "T\tAGENTS.md" {
		t.Fatalf("staged %q, want a type change of AGENTS.md", status)
	}

	code, stderr := run(t, PreCommit)
	if code != 1 || !strings.Contains(stderr, "git rm -r --cached -- AGENTS.md") {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
}

func TestPreCommit_refusesAnyPathBelowTheAgentConfigFolders(t *testing.T) {
	_, root := delivered(t)
	writeFile(t, filepath.Join(root, ".claude", "local notes.md"), "mine\n")
	kayatest.Git(t, root, "add", "-f", ".claude/local notes.md", ".kaya/state.json")

	code, stderr := run(t, PreCommit)
	if code != 1 || !strings.Contains(stderr, "git rm -r --cached -- '.claude/local notes.md'\n") ||
		!strings.Contains(stderr, "git rm -r --cached -- .kaya/state.json\n") {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
}

func TestUnderGuardedDir(t *testing.T) {
	for path, want := range map[string]bool{
		".agents/rules/core.md": true, ".claude": true, ".KAYA/x": true, ".Claude/settings.json": true,
		"docs/.claude/x": false, ".agentsx/y": false, "AGENTS.md": false, "src/.kaya": false,
	} {
		if got := underGuardedDir(path); got != want {
			t.Errorf("underGuardedDir(%q) = %v, want %v", path, got, want)
		}
	}
}

func TestPreCommit_passesOrdinaryChangesAndDeletions(t *testing.T) {
	_, root := delivered(t)
	kayatest.Git(t, root, "add", "-f", "AGENTS.md")
	kayatest.Git(t, root, "commit", "-q", "-m", "track the link")
	writeFile(t, filepath.Join(root, "src", "main.go"), "package main\n")
	kayatest.Git(t, root, "add", "src/main.go")
	kayatest.Git(t, root, "rm", "-q", "--cached", "AGENTS.md")

	if code, stderr := run(t, PreCommit); code != 0 || stderr != "" {
		t.Fatalf("exit %d, stderr %q", code, stderr)
	}
}

func TestPreCommit_exitsZeroOutsideADeliveredCheckout(t *testing.T) {
	_, root := delivered(t)
	writeFile(t, filepath.Join(root, ".claude", "settings.local.json"), "{}\n")
	kayatest.Git(t, root, "add", "-f", ".claude/settings.local.json", "AGENTS.md")

	t.Run("not cut over", func(t *testing.T) {
		writeFile(t, filepath.Join(root, ".agents", ".managed.json"), "{}\n")
		t.Cleanup(func() { _ = os.Remove(filepath.Join(root, ".agents", ".managed.json")) })
		if code, stderr := run(t, PreCommit); code != 0 || stderr != "" {
			t.Fatalf("exit %d, stderr %q", code, stderr)
		}
	})
	t.Run("not on the repository list", func(t *testing.T) {
		kayatest.ListRepositories(t, kayatest.Organization, "github.com/example/other")
		t.Cleanup(func() { kayatest.ListRepositories(t, kayatest.Organization, kayatest.Remote) })
		if code, stderr := run(t, PreCommit); code != 0 || stderr != "" {
			t.Fatalf("exit %d, stderr %q", code, stderr)
		}
	})
	t.Run("outside git", func(t *testing.T) {
		chdir(t, t.TempDir())
		if code, stderr := run(t, PreCommit); code != 0 || stderr != "" {
			t.Fatalf("exit %d, stderr %q", code, stderr)
		}
	})
}

func TestPostCheckout_deliversANewWorktreeOnline(t *testing.T) {
	kaya, root := delivered(t)
	kaya.Publish(t, 2, map[string]string{"AGENTS.md": "# Rules 2\n", ".agents/skills/demo/SKILL.md": skillContent})
	head := gitOutput(t, root, "rev-parse", "HEAD")
	worktree := addWorktree(t, root, "feature")
	chdir(t, worktree)

	code, stderr := run(t, PostCheckout, nullSHA, head, "1")
	if code != 0 || !strings.Contains(stderr, "config release 2 placed in") {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
	if got := readContent(t, filepath.Join(worktree, "AGENTS.md")); got != "# Rules 2\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if state := loadState(t, worktree); state.Active != 2 || state.CheckedAt == "" {
		t.Fatalf("state = %+v", state)
	}
}

func TestPostCheckout_deliversANewWorktreeOfflineFromTheMainWorktree(t *testing.T) {
	kaya, root := delivered(t)
	head := gitOutput(t, root, "rev-parse", "HEAD")
	mainState := loadState(t, root)
	worktree := addWorktree(t, root, "feature")
	unreachable(t)
	requests := kaya.Requests.Load()
	chdir(t, worktree)

	code, stderr := run(t, PostCheckout, nullSHA, head, "1")
	if code != 0 || !strings.Contains(stderr, "no release came from Kaya") || !strings.Contains(stderr, "config release 1, which "+realPath(t, root)) {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
	if got := readContent(t, filepath.Join(worktree, "AGENTS.md")); got != agentsContent {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if got := readContent(t, filepath.Join(worktree, ".agents", "skills", "demo", "SKILL.md")); got != skillContent {
		t.Fatalf("SKILL.md = %q", got)
	}
	state := loadState(t, worktree)
	if state.Active != 1 || state.CheckedAt != mainState.CheckedAt || state.ETag != "" {
		t.Fatalf("state = %+v, main worktree's = %+v", state, mainState)
	}
	if kaya.Requests.Load() != requests {
		t.Fatalf("the offline delivery called the fake Kaya")
	}
}

// floorOf is this laptop's release floor of the repository whose state is given.
func floorOf(t *testing.T, state delivery.State) int {
	t.Helper()
	floors, err := delivery.LoadFloors()
	if err != nil {
		t.Fatal(err)
	}
	return floors.Floor(state.Organization, state.RemoteURL)
}

// setFloor makes number this laptop's release floor of the repository whose state is given,
// lower or higher than the floor it had.
func setFloor(t *testing.T, state delivery.State, number int) {
	t.Helper()
	path, err := delivery.FloorsPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
		t.Fatal(err)
	}
	if err := delivery.RaiseFloor(context.Background(), state.Organization, state.RemoteURL, number); err != nil {
		t.Fatal(err)
	}
	if got := floorOf(t, state); got != number {
		t.Fatalf("floor = %d, want %d", got, number)
	}
}

func TestPostCheckout_offlineRefusesAMainWorktreeReleaseBelowTheFloor(t *testing.T) {
	kaya, root := delivered(t)
	head := gitOutput(t, root, "rev-parse", "HEAD")
	mainState := loadState(t, root)
	if got := floorOf(t, mainState); got != 1 {
		t.Fatalf("floor after the main worktree's sync = %d, want 1", got)
	}
	// Another checkout of the repository on this laptop verified release 2, so release 1, which
	// the main worktree still has active, is a rollback.
	setFloor(t, mainState, 2)
	worktree := addWorktree(t, root, "feature")
	unreachable(t)
	requests := kaya.Requests.Load()
	chdir(t, worktree)

	code, stderr := run(t, PostCheckout, nullSHA, head, "1")

	if code != 0 || !strings.Contains(stderr, "oba kaya git-hook post-checkout: no release came from Kaya") ||
		!strings.Contains(stderr, "release 1 of "+realPath(t, root)+" was not taken") ||
		!strings.Contains(stderr, "release 1 is older than release 2") ||
		!strings.Contains(stderr, "run oba kaya sync in this checkout") {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
	for _, path := range []string{"AGENTS.md", filepath.Join(".agents", "skills", "demo", "SKILL.md"), filepath.Join(".kaya", "current")} {
		if _, err := os.Lstat(filepath.Join(worktree, path)); !errors.Is(err, os.ErrNotExist) {
			t.Fatalf("%s is in the worktree (err %v)", path, err)
		}
	}
	if state := loadState(t, worktree); state.Active != 0 {
		t.Fatalf("state = %+v", state)
	}
	if got := floorOf(t, mainState); got != 2 {
		t.Fatalf("floor = %d, want 2", got)
	}
	if kaya.Requests.Load() != requests {
		t.Fatal("the offline delivery called the fake Kaya")
	}
}

func TestPostCheckout_offlinePlacesAMainWorktreeReleaseAtOrAboveTheFloor(t *testing.T) {
	for _, tc := range []struct {
		name  string
		floor int
	}{
		{name: "above the floor, which rises", floor: 1},
		{name: "at the floor", floor: 2},
	} {
		t.Run(tc.name, func(t *testing.T) {
			kaya, root := delivered(t)
			kaya.Publish(t, 2, map[string]string{"AGENTS.md": "# Rules 2\n", ".agents/skills/demo/SKILL.md": skillContent})
			if out, err := kayatest.Sync(t); err != nil {
				t.Fatalf("sync: %v\n%s", err, out)
			}
			head := gitOutput(t, root, "rev-parse", "HEAD")
			mainState := loadState(t, root)
			if mainState.Active != 2 {
				t.Fatalf("main worktree state = %+v, want release 2 active", mainState)
			}
			setFloor(t, mainState, tc.floor)
			worktree := addWorktree(t, root, "feature")
			// The common directory holds the user's own line and no block, so only the hook can
			// write the block this test reads.
			exclude := filepath.Join(gitOutput(t, worktree, "rev-parse", "--path-format=absolute", "--git-common-dir"), "info", "exclude")
			writeFile(t, exclude, "*.log\n")
			unreachable(t)
			requests := kaya.Requests.Load()
			chdir(t, worktree)

			code, stderr := run(t, PostCheckout, nullSHA, head, "1")

			if code != 0 || !strings.Contains(stderr, "config release 2, which "+realPath(t, root)+" has active, placed in") {
				t.Fatalf("exit %d, stderr:\n%s", code, stderr)
			}
			if got := readContent(t, filepath.Join(worktree, "AGENTS.md")); got != "# Rules 2\n" {
				t.Fatalf("AGENTS.md = %q", got)
			}
			if state := loadState(t, worktree); state.Active != 2 {
				t.Fatalf("state = %+v", state)
			}
			if got := floorOf(t, mainState); got != 2 {
				t.Fatalf("floor = %d, want 2", got)
			}
			content := readContent(t, exclude)
			lines := strings.Split(content, "\n")
			if !strings.HasPrefix(content, "*.log\n") || !slices.Contains(lines, delivery.ExcludeBegin) || !slices.Contains(lines, delivery.ExcludeEnd) {
				t.Fatalf("%s:\n%s", exclude, content)
			}
			for _, entry := range []string{"/.agents", "/.claude", "/.kaya", "/AGENTS.md"} {
				if !slices.Contains(lines, entry) {
					t.Fatalf("%s has no %s:\n%s", exclude, entry, content)
				}
			}
			own := filepath.Join(gitOutput(t, worktree, "rev-parse", "--absolute-git-dir"), "info", "exclude")
			if _, err := os.Lstat(own); !errors.Is(err, os.ErrNotExist) {
				t.Fatalf("the worktree has an exclude file of its own: %s (err %v)", own, err)
			}
			if status := gitOutput(t, worktree, "status", "--porcelain", "--untracked-files=all"); status != "" {
				t.Fatalf("git status in the worktree:\n%s", status)
			}
			if kaya.Requests.Load() != requests {
				t.Fatal("the offline delivery called the fake Kaya")
			}
		})
	}
}

func TestPostCheckout_refusesAMainWorktreeReleaseThatFailsVerification(t *testing.T) {
	_, root := delivered(t)
	head := gitOutput(t, root, "rev-parse", "HEAD")
	releaseFile := filepath.Join(root, ".kaya", "releases", "1.json")
	tampered := strings.Replace(readContent(t, releaseFile), `\"number\":1`, `\"number\":1,\"extra\":true`, 1)
	if tampered == readContent(t, releaseFile) {
		t.Fatal("the release file was not changed")
	}
	writeFile(t, releaseFile, tampered)
	worktree := addWorktree(t, root, "feature")
	unreachable(t)
	chdir(t, worktree)

	code, stderr := run(t, PostCheckout, nullSHA, head, "1")
	if code != 0 || !strings.Contains(stderr, "oba kaya git-hook post-checkout: no release came from Kaya") ||
		!strings.Contains(stderr, "release 1 of "+realPath(t, root)+" was not taken") {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
	if _, err := os.Lstat(filepath.Join(worktree, "AGENTS.md")); err == nil {
		t.Fatal("a release that failed verification was placed")
	}
}

func TestPostCheckout_aFailureWarnsAndExitsZero(t *testing.T) {
	t.Run("no release anywhere", func(t *testing.T) {
		_, root := delivered(t)
		head := gitOutput(t, root, "rev-parse", "HEAD")
		if err := os.RemoveAll(filepath.Join(root, ".kaya")); err != nil {
			t.Fatal(err)
		}
		worktree := addWorktree(t, root, "feature")
		unreachable(t)
		chdir(t, worktree)
		code, stderr := run(t, PostCheckout, nullSHA, head, "1")
		if code != 0 || !strings.Contains(stderr, "has no active release; run oba kaya sync in this checkout") {
			t.Fatalf("exit %d, stderr:\n%s", code, stderr)
		}
	})
	t.Run("a damaged state", func(t *testing.T) {
		_, root := delivered(t)
		head := gitOutput(t, root, "rev-parse", "HEAD")
		writeFile(t, filepath.Join(root, ".kaya", "state.json"), "{")
		code, stderr := run(t, PostCheckout, head, head, "1")
		if code != 0 || !strings.Contains(stderr, "state.json is damaged") {
			t.Fatalf("exit %d, stderr:\n%s", code, stderr)
		}
	})
}

func TestPostCheckout_aBranchSwitchPlacesTheReleaseAgain(t *testing.T) {
	kaya, root := delivered(t)
	// What git ignores it overwrites on a checkout, as it does with the delivered paths.
	exclude := filepath.Join(root, ".git", "info", "exclude")
	writeFile(t, exclude, readContent(t, exclude)+"/AGENTS.md\n/.agents\n/.kaya\n")
	main := gitOutput(t, root, "rev-parse", "HEAD")
	kayatest.Git(t, root, "switch", "-q", "-c", "before-kaya")
	if err := os.Remove(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(root, "AGENTS.md"), "# Tracked before Kaya\n")
	kayatest.Git(t, root, "add", "-f", "AGENTS.md")
	kayatest.Git(t, root, "commit", "-q", "-m", "tracked agent config")
	before := gitOutput(t, root, "rev-parse", "HEAD")
	kayatest.Git(t, root, "switch", "-q", "main")
	if _, err := os.Lstat(filepath.Join(root, "AGENTS.md")); err == nil {
		t.Fatal("the switch back left AGENTS.md; the test needs git to remove it")
	}
	requests := kaya.Requests.Load()

	code, stderr := run(t, PostCheckout, before, main, "1")
	if code != 0 || stderr != "" {
		t.Fatalf("exit %d, stderr:\n%s", code, stderr)
	}
	if got := readContent(t, filepath.Join(root, "AGENTS.md")); got != agentsContent {
		t.Fatalf("AGENTS.md = %q", got)
	}
	if kaya.Requests.Load() != requests {
		t.Fatal("placing the active release again called Kaya")
	}
}

func TestPostCheckout_aFileCheckoutLeavesDeliveryAlone(t *testing.T) {
	kaya, root := delivered(t)
	head := gitOutput(t, root, "rev-parse", "HEAD")
	if err := os.Remove(filepath.Join(root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	requests := kaya.Requests.Load()
	if code, stderr := run(t, PostCheckout, head, head, "0"); code != 0 || stderr != "" {
		t.Fatalf("exit %d, stderr %q", code, stderr)
	}
	if _, err := os.Lstat(filepath.Join(root, "AGENTS.md")); err == nil || kaya.Requests.Load() != requests {
		t.Fatal("a checkout of files changed delivery")
	}
}

func TestPostCheckout_doesNothingInACheckoutKayaDoesNotDeliverTo(t *testing.T) {
	kaya, root := delivered(t)
	head := gitOutput(t, root, "rev-parse", "HEAD")
	kayatest.ListRepositories(t, kayatest.Organization, "github.com/example/other")
	worktree := addWorktree(t, root, "feature")
	requests := kaya.Requests.Load()
	chdir(t, worktree)
	if code, stderr := run(t, PostCheckout, nullSHA, head, "1"); code != 0 || stderr != "" {
		t.Fatalf("exit %d, stderr %q", code, stderr)
	}
	if _, err := os.Lstat(filepath.Join(worktree, ".kaya")); err == nil || kaya.Requests.Load() != requests {
		t.Fatal("a checkout off the repository list got delivery")
	}
}

func TestRun_anUnknownEventWarnsAndExitsZero(t *testing.T) {
	code, stderr := run(t, "pre-push")
	if code != 0 || !strings.Contains(stderr, "oba kaya git-hook pre-push: unknown git hook") {
		t.Fatalf("exit %d, stderr %q", code, stderr)
	}
}
