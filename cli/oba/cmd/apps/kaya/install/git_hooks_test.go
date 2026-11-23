package install

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kaya/githook"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
)

// runAsOba makes the test binary act as oba, so that the git hooks install registers run the
// commands of this tree.
const runAsOba = "OBA_TEST_RUN_AS_OBA"

func TestMain(m *testing.M) {
	if os.Getenv(runAsOba) == "1" {
		os.Exit(runOba(os.Args[1:]))
	}
	os.Exit(m.Run())
}

// runOba runs args as the oba binary runs oba kaya git-hook, and returns its exit status.
func runOba(args []string) int {
	group := &cobra.Command{Use: "kaya"}
	group.AddCommand(githook.NewCmd())
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	root.SetArgs(args)
	err := root.Execute()
	var exitErr *exitcode.Error
	switch {
	case errors.As(err, &exitErr):
		return exitErr.Code
	case err != nil:
		_, _ = fmt.Fprintln(os.Stderr, "oba:", err)
		return 1
	}
	return 0
}

// requireConfigHooks skips a test that needs git to run hooks from the global git config, which
// git does from 2.54 on.
func requireConfigHooks(t *testing.T) {
	t.Helper()
	out, err := exec.Command("git", "--version").Output()
	if err != nil {
		t.Fatalf("git --version: %v", err)
	}
	match := regexp.MustCompile(`^git version (\d+)\.(\d+)`).FindStringSubmatch(string(out))
	if match == nil {
		t.Fatalf("unreadable git version %q", out)
	}
	major, _ := strconv.Atoi(match[1])
	minor, _ := strconv.Atoi(match[2])
	if major < 2 || major == 2 && minor < 54 {
		t.Skipf("%s runs no hooks from the git config; git 2.54 or newer does", strings.TrimSpace(string(out)))
	}
}

// hookedCheckout sets up a delivered checkout with release 1 in place, a global git config of the
// test, the test binary on PATH as oba, and a repository hooks folder in core.hooksPath whose
// pre-commit leaves a mark. It returns the fake Kaya, the checkout, the oba path and the mark.
func hookedCheckout(t *testing.T) (*kayatest.FakeKaya, string, string, string) {
	t.Helper()
	kaya, root := kayatest.SetupDelivery(t)
	for key, value := range map[string]string{
		"GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "test@example.com",
		"GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "test@example.com",
		"GIT_CONFIG_GLOBAL": filepath.Join(t.TempDir(), "gitconfig"), "TMPDIR": t.TempDir(),
	} {
		t.Setenv(key, value)
	}
	previous := effectiveUID
	effectiveUID = func() int { return 1000 }
	t.Cleanup(func() { effectiveUID = previous })

	git, err := exec.LookPath("git")
	if err != nil {
		t.Fatal(err)
	}
	self, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	bin := t.TempDir()
	oba := filepath.Join(bin, "oba")
	if err := os.Symlink(self, oba); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", bin+string(os.PathListSeparator)+filepath.Dir(git))
	t.Setenv(runAsOba, "1")

	if err := os.WriteFile(filepath.Join(root, "README.md"), []byte("demo\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	kayatest.Git(t, root, "add", "README.md")
	kayatest.Git(t, root, "commit", "-q", "-m", "first")
	kaya.Publish(t, 1, map[string]string{"AGENTS.md": "# Rules\n", ".agents/skills/demo/SKILL.md": "---\nname: demo\n---\n"})
	if out, err := kayatest.Sync(t); err != nil {
		t.Fatalf("sync: %v\n%s", err, out)
	}

	hooks := t.TempDir()
	mark := filepath.Join(t.TempDir(), "repository-hook-ran")
	if err := os.WriteFile(filepath.Join(hooks, "pre-commit"), []byte("#!/bin/sh\ntouch '"+mark+"'\n"), 0o700); err != nil { // #nosec G306 -- a hook git must run
		t.Fatal(err)
	}
	kayatest.Git(t, root, "config", "core.hooksPath", hooks)
	return kaya, root, oba, mark
}

// gitRun runs git in dir and returns its output and error, for a command a hook may refuse.
func gitRun(dir string, args ...string) (string, error) {
	out, err := exec.Command("git", append([]string{"-C", dir}, args...)...).CombinedOutput()
	return string(out), err
}

func readText(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func TestInstall_writesKayaGitHooksOnceAndGitRunsThemBesideCoreHooksPath(t *testing.T) {
	requireConfigHooks(t)
	_, root, oba, mark := hookedCheckout(t)

	out, err := run(t)
	if err != nil {
		t.Fatalf("install: %v\n%s", err, out)
	}
	for _, want := range []string{
		"Changed the global git config: set hook.kaya-pre-commit.event to pre-commit\n",
		"Changed the global git config: set hook.kaya-post-checkout.event to post-checkout\n",
		"Changed the global git config: set hook.kaya-pre-commit.command to [ -x " + oba + " ] || ",
		"; exec " + oba + " kaya git-hook post-checkout\n",
		"Refreshed the repository list: 1 repositories of the organization.\n",
	} {
		if !strings.Contains(out, want) {
			t.Fatalf("install output lacks %q:\n%s", want, out)
		}
	}
	config := readText(t, os.Getenv("GIT_CONFIG_GLOBAL"))

	out, err = run(t)
	if err != nil {
		t.Fatalf("second install: %v\n%s", err, out)
	}
	if strings.Contains(out, "the global git config: set") || !strings.Contains(out, "Already set: the global git config sets hook.kaya-post-checkout.command\n") {
		t.Fatalf("a second install changes the git hooks:\n%s", out)
	}
	if again := readText(t, os.Getenv("GIT_CONFIG_GLOBAL")); again != config {
		t.Fatalf("a second install changed the global git config:\n%s", again)
	}

	kayatest.Git(t, root, "add", "-f", "AGENTS.md")
	out, err = gitRun(root, "commit", "-m", "track the delivered file")
	if err == nil || !strings.Contains(out, "git rm -r --cached -- AGENTS.md") {
		t.Fatalf("git commit was not refused: %v\n%s", err, out)
	}
	if _, err := os.Stat(mark); err != nil {
		t.Fatalf("the hook of core.hooksPath did not run beside Kaya's: %v", err)
	}
	kayatest.Git(t, root, "rm", "-q", "-r", "--cached", "--", "AGENTS.md")
	if out, err := gitRun(root, "commit", "-q", "--allow-empty", "-m", "nothing delivered"); err != nil {
		t.Fatalf("a commit without delivered paths was refused: %v\n%s", err, out)
	}

	worktree := filepath.Join(t.TempDir(), "feature")
	if out, err := gitRun(root, "worktree", "add", "-q", "-b", "feature", worktree); err != nil {
		t.Fatalf("git worktree add: %v\n%s", err, out)
	}
	if got := readText(t, filepath.Join(worktree, "AGENTS.md")); got != "# Rules\n" {
		t.Fatalf("the new worktree's AGENTS.md = %q", got)
	}
}

func TestInstall_aMissingObaLetsGitGoOnAndNamesInstall(t *testing.T) {
	requireConfigHooks(t)
	_, root, oba, _ := hookedCheckout(t)
	if out, err := run(t); err != nil {
		t.Fatalf("install: %v\n%s", err, out)
	}
	if err := os.Remove(oba); err != nil {
		t.Fatal(err)
	}
	kayatest.Git(t, root, "add", "-f", "AGENTS.md")
	out, err := gitRun(root, "commit", "-m", "with oba gone")
	if err != nil {
		t.Fatalf("a missing oba blocked the commit: %v\n%s", err, out)
	}
	want := "oba kaya: " + oba + " is missing, so the Kaya git hook pre-commit did not run; run oba kaya install"
	if !strings.Contains(out, want) {
		t.Fatalf("git output lacks %q:\n%s", want, out)
	}
	out, err = gitRun(root, "worktree", "add", "-q", "-b", "feature", filepath.Join(t.TempDir(), "feature"))
	if err != nil || !strings.Contains(out, "the Kaya git hook post-checkout did not run; run oba kaya install") {
		t.Fatalf("git worktree add: %v\n%s", err, out)
	}
}

func TestInstall_aFailedRepositoryListRefreshIsAWarning(t *testing.T) {
	hookedCheckout(t)
	t.Setenv(machine.EnvToken, "kyt_refused")
	out, err := run(t)
	if err != nil {
		t.Fatalf("install failed: %v\n%s", err, out)
	}
	if !strings.Contains(out, "Warning: the repository list was not refreshed: Kaya refused the machine token") ||
		!strings.Contains(out, "Changed the global git config: set hook.kaya-pre-commit.event to pre-commit\n") {
		t.Fatalf("install output:\n%s", out)
	}
}
