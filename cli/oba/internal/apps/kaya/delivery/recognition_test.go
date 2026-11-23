package delivery

import (
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

// isolateConfig keeps the user's own configuration out of a test: git reads no global or system
// config, and the repository list lives below a scratch home.
func isolateConfig(t *testing.T) {
	t.Helper()
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git is not installed")
	}
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("XDG_CONFIG_HOME", filepath.Join(home, ".config"))
	t.Setenv("GIT_CONFIG_GLOBAL", os.DevNull)
	t.Setenv("GIT_CONFIG_NOSYSTEM", "1")
}

// gitCheckout is a new git checkout with remotes given as name, url pairs, added in that order.
func gitCheckout(t *testing.T, remotes ...string) string {
	t.Helper()
	root := t.TempDir()
	gitIn(t, root, "init", "-q")
	for i := 0; i+1 < len(remotes); i += 2 {
		gitIn(t, root, "remote", "add", remotes[i], remotes[i+1])
	}
	return root
}

func gitIn(t *testing.T, dir string, args ...string) {
	t.Helper()
	cmd := exec.Command("git", append([]string{"-C", dir}, args...)...)
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, out)
	}
}

// listRemotes makes the cached repository list hold exactly remotes.
func listRemotes(t *testing.T, organization string, remotes ...string) {
	t.Helper()
	if err := SaveRepositoryList(RepositoryList{OrganizationID: organization, Remotes: remotes}); err != nil {
		t.Fatal(err)
	}
}

func mustFind(t *testing.T, dir string) *Checkout {
	t.Helper()
	checkout, err := FindCheckout(dir)
	if err != nil {
		t.Fatalf("FindCheckout(%s): %v", dir, err)
	}
	return checkout
}

func TestFindCheckout_originNamesTheRepositoryBeforeOtherRemotes(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t,
		"upstream", "https://github.com/example/app.git",
		"origin", "git@github.com:example/tool.git",
	)
	listRemotes(t, testOrg, "github.com/example/app", "github.com/example/tool")
	checkout := mustFind(t, root)
	if checkout.Kind != Delivered || checkout.RemoteName != "origin" || checkout.Remote != "github.com/example/tool" {
		t.Fatalf("checkout = %+v, want Delivered by origin", checkout)
	}
	if checkout.Organization != testOrg {
		t.Fatalf("organization = %q, want the list's %q", checkout.Organization, testOrg)
	}
}

func TestFindCheckout_withoutAListedOriginTheOtherRemotesCountInNameOrder(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t,
		"zeta", "https://github.com/example/zeta.git",
		"origin", "https://github.com/someone/elsewhere.git",
		"alpha", "https://github.com/example/alpha.git",
	)
	listRemotes(t, testOrg, "github.com/example/alpha", "github.com/example/zeta")
	checkout := mustFind(t, root)
	if checkout.Kind != Delivered || checkout.RemoteName != "alpha" || checkout.Remote != "github.com/example/alpha" {
		t.Fatalf("checkout = %+v, want Delivered by alpha", checkout)
	}
}

func TestFindCheckout_aForkIsRecognisedByItsUpstream(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t,
		"origin", "git@github.com:someone/app.git",
		"upstream", "https://github.com/Example/App.git",
	)
	listRemotes(t, testOrg, "github.com/example/app")
	checkout := mustFind(t, filepath.Join(root, "src", "deep"))
	if checkout.Kind != Delivered || checkout.Root != root || checkout.RemoteName != "upstream" {
		t.Fatalf("checkout = %+v, want Delivered by upstream", checkout)
	}
	request, normalized, err := checkout.Remotes()
	if err != nil || request != "https://github.com/Example/App.git" || normalized != "github.com/example/app" {
		t.Fatalf("Remotes = %q, %q, %v", request, normalized, err)
	}
}

func TestFindCheckout_aLinkedWorktreeIsRecognisedLikeItsMainCheckout(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t, "origin", "https://github.com/example/app.git")
	listRemotes(t, testOrg, "github.com/example/app")
	gitIn(t, root, "-c", "user.name=Dev", "-c", "user.email=dev@example.com", "commit", "-q", "--allow-empty", "-m", "init")
	worktree := filepath.Join(t.TempDir(), "feature")
	gitIn(t, root, "worktree", "add", "-q", worktree, "-b", "feature")
	checkout := mustFind(t, worktree)
	if checkout.Kind != Delivered || checkout.Root != worktree || checkout.Remote != "github.com/example/app" {
		t.Fatalf("checkout = %+v, want the worktree Delivered", checkout)
	}
}

func TestFindCheckout_onlyTheCheckoutsOwnConfigCounts(t *testing.T) {
	isolateConfig(t)
	listRemotes(t, testOrg, "github.com/example/app")
	global := filepath.Join(t.TempDir(), "gitconfig")
	if err := os.WriteFile(global, []byte("[remote \"origin\"]\n\turl = https://github.com/example/app.git\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("GIT_CONFIG_GLOBAL", global)
	t.Setenv("GIT_CONFIG_COUNT", "1")
	t.Setenv("GIT_CONFIG_KEY_0", "remote.extra.url")
	t.Setenv("GIT_CONFIG_VALUE_0", "https://github.com/example/app.git")
	root := gitCheckout(t, "origin", "https://github.com/someone/own.git")
	if checkout := mustFind(t, root); checkout.Kind != NotKaya {
		t.Fatalf("checkout = %+v, want NotKaya: a listed remote outside the checkout's config counted", checkout)
	}

	// A GIT_DIR of another repository, as a git hook runs under, is not followed either way.
	other := gitCheckout(t, "origin", "https://github.com/example/app.git")
	t.Setenv("GIT_DIR", filepath.Join(other, ".git"))
	if checkout := mustFind(t, root); checkout.Kind != NotKaya {
		t.Fatalf("checkout = %+v, want NotKaya under another repository's GIT_DIR", checkout)
	}
	t.Setenv("GIT_DIR", filepath.Join(root, ".git"))
	if checkout := mustFind(t, other); checkout.Kind != Delivered {
		t.Fatalf("checkout = %+v, want Delivered under another repository's GIT_DIR", checkout)
	}
}

func TestFindCheckout_notKayaNamesNoRemote(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t, "origin", "https://dev:secret@github.com/someone/private.git")
	listRemotes(t, testOrg, "github.com/example/app")
	checkout := mustFind(t, root)
	if checkout.Kind != NotKaya || checkout.Remote != "" || checkout.RemoteName != "" || checkout.Organization != "" {
		t.Fatalf("checkout = %+v, want NotKaya with no remote", checkout)
	}
	request, normalized, err := checkout.Remotes()
	if err == nil || request != "" || normalized != "" {
		t.Fatalf("Remotes = %q, %q, %v; want an error and nothing to send", request, normalized, err)
	}
	for _, text := range []string{err.Error(), NotDelivered(checkout).Error(), Recognition(checkout)} {
		if strings.Contains(text, "someone") || strings.Contains(text, "secret") {
			t.Fatalf("%q names the checkout's remote", text)
		}
	}
}

func TestFindCheckout_userInfoIsNeverSent(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t, "origin", "https://dev:secret@github.com/example/app.git")
	listRemotes(t, testOrg, "github.com/example/app")
	request, normalized, err := mustFind(t, root).Remotes()
	if err != nil || request != "https://github.com/example/app.git" || normalized != "github.com/example/app" {
		t.Fatalf("Remotes = %q, %q, %v", request, normalized, err)
	}
}

func TestFindCheckout_aCommittedKayaYAMLIsIgnored(t *testing.T) {
	isolateConfig(t)
	listRemotes(t, testOrg, "github.com/example/app")
	unlisted := gitCheckout(t, "origin", "https://github.com/someone/own.git")
	listed := gitCheckout(t, "origin", "https://github.com/example/app.git")
	for _, root := range []string{unlisted, listed} {
		config := "remoteUrl: https://github.com/example/other.git\norganization: another-org\n"
		if err := os.WriteFile(filepath.Join(root, ConfigFile), []byte(config), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	if checkout := mustFind(t, unlisted); checkout.Kind != NotKaya {
		t.Fatalf("checkout = %+v, want NotKaya despite its kaya.yaml", checkout)
	}
	checkout := mustFind(t, listed)
	if checkout.Kind != Delivered || checkout.Remote != "github.com/example/app" || checkout.Organization != testOrg {
		t.Fatalf("checkout = %+v, want Delivered by its remote and the list's organization", checkout)
	}
}

func TestFindCheckout_withoutACachedListEveryCheckoutIsNotKaya(t *testing.T) {
	isolateConfig(t)
	// A bare .git directory makes every git call fail, so this also shows that no git ran.
	root := t.TempDir()
	if err := os.Mkdir(filepath.Join(root, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	checkout, err := FindCheckout(root)
	if err != nil || checkout.Kind != NotKaya || checkout.Root != root {
		t.Fatalf("checkout = %+v, %v; want NotKaya without an error", checkout, err)
	}
	if err := NotDelivered(checkout); err == nil || !strings.Contains(err.Error(), "repository list") {
		t.Fatalf("NotDelivered = %v", err)
	}
}

func TestFindCheckout_aDamagedListIsNamedAndTheCheckoutIsNotKaya(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t, "origin", "https://github.com/example/app.git")
	path, err := RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	checkout, err := FindCheckout(root)
	if err == nil || !strings.Contains(err.Error(), path) {
		t.Fatalf("err = %v, want one naming %s", err, path)
	}
	if checkout == nil || checkout.Kind != NotKaya || checkout.Root != root {
		t.Fatalf("checkout = %+v, want the root as NotKaya", checkout)
	}
}

func TestRecognize_anSSHAliasIsResolvedToTheListedHost(t *testing.T) {
	list := RepositoryList{OrganizationID: testOrg, Remotes: []string{"github.com/example/app"}}
	lookup := func(host string) (string, error) {
		switch host {
		case "work":
			return "github.com", nil
		case "github.com":
			return "github.com", nil
		}
		return "", errors.New("no such host")
	}
	c := &Checkout{Root: "/src/app", Kind: NotKaya}
	c.recognize(list, []gitRemote{{name: "origin", url: "git@broken:someone/app.git"}, {name: "work", url: "git@work:Example/app.git"}}, lookup)
	if c.Remote != "github.com/example/app" || c.RemoteName != "work" || c.requestRemote != "git@github.com:Example/app.git" {
		t.Fatalf("checkout = %+v, want the alias remote resolved and recognised", c)
	}
	if len(c.Unresolved) != 1 || !strings.Contains(c.Unresolved[0], "broken") {
		t.Fatalf("unresolved = %v, want the broken host named", c.Unresolved)
	}

	// A remote on the list as written is taken without asking ssh.
	asked := false
	c = &Checkout{Root: "/src/app", Kind: NotKaya}
	c.recognize(list, []gitRemote{{name: "origin", url: "git@github.com:example/app.git"}}, func(string) (string, error) {
		asked = true
		return "", nil
	})
	if c.Remote != "github.com/example/app" || asked {
		t.Fatalf("checkout = %+v, ssh asked = %v", c, asked)
	}
}

func TestGitRemotes_originFirstThenByNameEachRemotesURLsInOrder(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t,
		"b", "https://github.com/example/b.git",
		"origin", "https://github.com/example/origin.git",
		"a", "https://github.com/example/a.git",
	)
	gitIn(t, root, "config", "--add", "remote.a.url", "https://github.com/example/a2.git")
	remotes, err := gitRemotes(root)
	if err != nil {
		t.Fatal(err)
	}
	var got []string
	for _, remote := range remotes {
		got = append(got, remote.name+"="+remote.url)
	}
	want := []string{
		"origin=https://github.com/example/origin.git", "a=https://github.com/example/a.git",
		"a=https://github.com/example/a2.git", "b=https://github.com/example/b.git",
	}
	if !slices.Equal(got, want) {
		t.Fatalf("remotes = %v, want %v", got, want)
	}
	none, err := gitRemotes(gitCheckout(t))
	if err != nil || len(none) != 0 {
		t.Fatalf("a checkout without remotes = %v, %v", none, err)
	}
}
