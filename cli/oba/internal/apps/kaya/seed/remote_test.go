package seed

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// aliasLookup answers as `ssh -G` does under a configuration with one alias: github-sensey
// stands for github.com, and any other host comes back lowercased.
func aliasLookup(asked *[]string) func(string) (string, error) {
	return func(host string) (string, error) {
		*asked = append(*asked, host)
		if host == "github-sensey" {
			return "github.com", nil
		}
		return strings.ToLower(host), nil
	}
}

func TestRemoteResolver_formsAndFallbacks(t *testing.T) {
	var asked []string
	resolver := &RemoteResolver{Lookup: aliasLookup(&asked)}
	for _, tc := range []struct{ remote, want string }{
		{"git@github-sensey:senseylabs/village.git", "git@github.com:senseylabs/village.git"},
		{"ssh://git@github-sensey:2222/senseylabs/village.git", "ssh://git@github.com:2222/senseylabs/village.git"},
		{"git+ssh://github-sensey/senseylabs/village.git", "git+ssh://github.com/senseylabs/village.git"},
		{"github-sensey:senseylabs/village", "github.com:senseylabs/village"},
		// A host ssh only lowercases is kept as written.
		{"git@GitHub.com:senseylabs/village.git", "git@GitHub.com:senseylabs/village.git"},
		{"https://github-sensey/senseylabs/village.git", "https://github-sensey/senseylabs/village.git"},
		{"/srv/git/village.git", "/srv/git/village.git"},
		{"file:///srv/git/village.git", "file:///srv/git/village.git"},
		{"github-sensey://senseylabs/village", "github-sensey://senseylabs/village"},
		// A host that looks like an ssh option never reaches ssh.
		{"-oProxyCommand=touch:x", "-oProxyCommand=touch:x"},
		{"ssh://-oProxyCommand=touch/x", "ssh://-oProxyCommand=touch/x"},
		{"git@[::1]:a/b", "git@[::1]:a/b"},
		{"", ""},
	} {
		got, warning := resolver.Resolve(tc.remote)
		if got != tc.want || warning != "" {
			t.Errorf("Resolve(%q) = %q, %q; want %q and no warning", tc.remote, got, warning, tc.want)
		}
	}
	for _, host := range asked {
		if strings.HasPrefix(host, "-") || strings.Contains(host, "[") {
			t.Errorf("ssh was asked about %q", host)
		}
	}
	if len(asked) != 2 || asked[0] != "github-sensey" || asked[1] != "GitHub.com" {
		t.Errorf("hosts looked up = %v; each host once, from the cache after that", asked)
	}
}

func TestRemoteResolver_failureSendsTheRawRemoteAndWarnsOncePerHost(t *testing.T) {
	calls := 0
	resolver := &RemoteResolver{Lookup: func(string) (string, error) {
		calls++
		return "", errors.New("ssh -G github-sensey exited 255: broken")
	}}
	raw := "git@github-sensey:senseylabs/village.git"
	got, warning := resolver.Resolve(raw)
	if got != raw || !strings.Contains(warning, "exited 255") {
		t.Errorf("first Resolve = %q, %q; want the raw remote and the reason", got, warning)
	}
	got, warning = resolver.Resolve("ssh://git@github-sensey/senseylabs/kaya.git")
	if got != "ssh://git@github-sensey/senseylabs/kaya.git" || warning != "" || calls != 1 {
		t.Errorf("second Resolve = %q, %q after %d lookups; want the raw remote, no warning, one lookup", got, warning, calls)
	}
}

// fakeSSH puts an `ssh` running script first on PATH and returns the file its arguments go to.
func fakeSSH(t *testing.T, script string) string {
	t.Helper()
	dir := t.TempDir()
	args := filepath.Join(dir, "args")
	body := "#!/bin/sh\necho \"$@\" > '" + args + "'\n" + script + "\n"
	if err := os.WriteFile(filepath.Join(dir, "ssh"), []byte(body), 0o700); err != nil { // #nosec G306 -- the fake ssh must be executable
		t.Fatal(err)
	}
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
	return args
}

func TestSSHHostname(t *testing.T) {
	args := fakeSSH(t, `printf 'user git\nhostname github.com\nport 22\n'`)
	got, err := SSHHostname("github-sensey", SSHResolveTimeout)
	if err != nil || got != "github.com" {
		t.Fatalf("SSHHostname = %q, %v; want github.com", got, err)
	}
	called, err := os.ReadFile(args) // #nosec G304 -- a file of the test's own temp dir
	if err != nil {
		t.Fatal(err)
	}
	if string(called) != "-G -- github-sensey\n" {
		t.Errorf("ssh called with %q", called)
	}
}

func TestSSHHostname_failures(t *testing.T) {
	for _, tc := range []struct{ name, script, want string }{
		{"non-zero exit", "echo broken >&2; exit 255", "exited 255: broken"},
		{"no hostname line", "echo 'user git'", "printed no hostname"},
		{"unusable hostname", "echo 'hostname -oProxyCommand=x'", "not a plain host name"},
		{"timeout", "sleep 3", "timed out"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			fakeSSH(t, tc.script)
			started := time.Now()
			got, err := SSHHostname("github-sensey", 300*time.Millisecond)
			if err == nil || got != "" || !strings.Contains(err.Error(), tc.want) {
				t.Errorf("SSHHostname = %q, %v; want an error containing %q", got, err, tc.want)
			}
			if elapsed := time.Since(started); elapsed > 2*time.Second {
				t.Errorf("SSHHostname took %s", elapsed)
			}
		})
	}
	t.Run("ssh missing", func(t *testing.T) {
		t.Setenv("PATH", t.TempDir())
		if got, err := SSHHostname("github-sensey", SSHResolveTimeout); err == nil || got != "" {
			t.Errorf("SSHHostname = %q, %v; want an error", got, err)
		}
	})
}

func TestBuild_sendsTheRemoteWithItsRealHost(t *testing.T) {
	agents, repo := fixture(t)
	git(t, repo, "remote", "set-url", "origin", "git@github-sensey:senseylabs/village.git")
	var asked []string
	plan, err := Build(Options{AgentsDir: agents, RepoDirs: []string{repo}, SourceRef: "manual", Resolver: &RemoteResolver{Lookup: aliasLookup(&asked)}})
	if err != nil {
		t.Fatal(err)
	}
	ws := plan.Request.Repositories[0]
	if ws.RemoteURL != "git@github.com:senseylabs/village.git" {
		t.Errorf("remote = %q, want git@github.com:senseylabs/village.git", ws.RemoteURL)
	}
	if plan.Repositories[0].RemoteURL != ws.RemoteURL {
		t.Errorf("repository remote = %q, want the one sent", plan.Repositories[0].RemoteURL)
	}
}
