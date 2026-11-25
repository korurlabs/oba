package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/install"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// obaVersionUnderTest is the version the running oba and the fake oba on PATH report.
const obaVersionUnderTest = "1.4.0 (abc1234)"

// fakeOba stands in for the oba the drop-in and the git hooks run: it reports its version and
// does nothing else.
const fakeOba = `#!/bin/sh
if [ "$1" = "--version" ]; then
  printf 'oba version %s\n' "$FAKE_OBA_VERSION"
fi
exit 0
`

// fakeClaude stands in for claude -p. A session started with --setting-sources is the probe
// session; one without it loads the user settings. FAKE_CLAUDE_START, FAKE_CLAUDE_AGENTS_MD and
// FAKE_CLAUDE_RECORD list the sessions, "probe" and "user", that start, that write the agents-md
// line of the debug log, and that queue a record event as a record hook does; "no" lists none.
// A session that starts prints the init event with the skills and subagents the test names. Every
// session logs a hook's output that repeats the agents-md plugin's words, which proves nothing. As
// Claude Code does, it refuses --strict-mcp-config where the file FAKE_CLAUDE_MANAGED_MCP exists.
const fakeClaude = `#!/bin/sh
printf '%s\n' "$*" >> "$FAKE_CLAUDE_ARGS"
session=""
debug=""
kind="user"
strict=""
while [ $# -gt 0 ]; do
  case "$1" in
    --session-id) session="$2"; shift ;;
    --debug-file) debug="$2"; shift ;;
    --setting-sources) kind="probe"; shift ;;
    --strict-mcp-config) strict="yes" ;;
  esac
  shift
done
does() {
  case " $1 " in
    *" $kind "*) return 0 ;;
  esac
  return 1
}
if [ -n "$strict" ] && [ -f "$FAKE_CLAUDE_MANAGED_MCP" ]; then
  echo "--strict-mcp-config cannot replace the managed MCP servers" >&2
  exit 1
fi
if ! does "$FAKE_CLAUDE_START"; then
  echo "Invalid API key" >&2
  exit 1
fi
printf '{"type":"system","subtype":"init","cwd":"%s","session_id":"%s","skills":%s,"agents":%s}\n' "$PWD" "$session" "$FAKE_CLAUDE_SKILLS" "$FAKE_CLAUDE_AGENTS"
printf '2026-11-24T10:53:35.500Z [DEBUG] Session started\n' > "$debug"
printf '2026-11-24T10:53:35.715Z [DEBUG] Hook SessionStart:startup stdout: no CLAUDE.md found; AGENTS.md loaded: %s/AGENTS.md\n' "$PWD" >> "$debug"
if does "$FAKE_CLAUDE_AGENTS_MD"; then
  printf '2026-11-24T10:53:35.815Z [DEBUG] [cc-plugin-agents-md] $.ui.log (to debug): no CLAUDE.md found; AGENTS.md loaded: %s/AGENTS.md\n' "$PWD" >> "$debug"
fi
if does "$FAKE_CLAUDE_RECORD"; then
  dir="$XDG_STATE_HOME/kaya/queue/sessions/$session"
  /bin/mkdir -p "$dir"
  printf '{"seq":1,"event":"SessionStart"}\n' >> "$dir/events.jsonl"
fi
printf '{"type":"result","subtype":"success","result":"OK"}\n'
`

// doctorEnv is a laptop on which oba kaya doctor finds nothing wrong: a delivered checkout, the
// drop-in in a managed folder of the test, owned by root as far as doctor sees, the git hook
// entries in a global git config of the test, claude() in ~/.bashrc, the in-force proof, and a
// signing key that oba trusts.
type doctorEnv struct {
	kaya    *kayatest.FakeKaya
	root    string
	bin     string
	managed string
	opts    install.Options
	dropIn  []byte
	// uid and mode override what doctor sees of a path's owner and permission bits.
	uid     map[string]uint32
	mode    map[string]fs.FileMode
	keys    []client.SigningKey
	keysErr error
	oldGit  bool
}

// fakeInfo is file information with the owner and mode a test gives it.
type fakeInfo struct {
	fs.FileInfo
	uid  uint32
	mode fs.FileMode
}

func (f fakeInfo) Mode() fs.FileMode { return f.mode }
func (f fakeInfo) Sys() any          { return &syscall.Stat_t{Uid: f.uid} }

// setupDoctor prepares the laptop with release 1 published but not yet synced.
func setupDoctor(t *testing.T) *doctorEnv {
	t.Helper()
	kaya, root := kayatest.SetupDelivery(t)
	e := &doctorEnv{kaya: kaya, root: root, uid: map[string]uint32{}, mode: map[string]fs.FileMode{}}
	for key, value := range map[string]string{
		"GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "test@example.com",
		"GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "test@example.com",
		"GIT_CONFIG_GLOBAL": filepath.Join(t.TempDir(), "gitconfig"), "SHELL": "/bin/bash", "TMPDIR": t.TempDir(),
		"FAKE_OBA_VERSION": obaVersionUnderTest, "FAKE_CLAUDE_ARGS": filepath.Join(t.TempDir(), "claude-args"),
		"FAKE_CLAUDE_SKILLS": `["notes","example:other"]`, "FAKE_CLAUDE_AGENTS": `["docs-helper","general-purpose"]`,
		// As Claude Code does: both sessions start and queue a record event, and only the session
		// with the user settings loads AGENTS.md.
		"FAKE_CLAUDE_START": "probe user", "FAKE_CLAUDE_RECORD": "probe user", "FAKE_CLAUDE_AGENTS_MD": "user",
	} {
		t.Setenv(key, value)
	}
	git, err := exec.LookPath("git")
	if err != nil {
		t.Fatal(err)
	}
	e.oldGit = gitOlderThanConfigHooks(t)
	e.bin = t.TempDir()
	t.Setenv("PATH", strings.Join([]string{e.bin, filepath.Dir(git), "/bin"}, string(os.PathListSeparator)))
	oba := writeExecutable(t, e.bin, "oba", fakeOba)

	kaya.Publish(t, 1, map[string]string{
		"AGENTS.md": "# Rules\n", ".agents/skills/demo/SKILL.md": "---\nname: demo\n---\n",
		".claude/skills/notes/SKILL.md": "---\nname: notes\n---\n", ".claude/agents/helper.md": "---\nname: docs-helper\n---\nHelps with the docs.\n",
	})

	e.managed = filepath.Join(t.TempDir(), "ClaudeCode", "managed-settings.d")
	t.Setenv("FAKE_CLAUDE_MANAGED_MCP", filepath.Join(filepath.Dir(e.managed), managedMCPFile))
	if err := os.MkdirAll(e.managed, 0o750); err != nil {
		t.Fatal(err)
	}
	for _, dir := range []string{e.managed, filepath.Dir(e.managed)} {
		if err := os.Chmod(dir, 0o755); err != nil { // #nosec G302 -- the managed folders are world-readable, as on a laptop
			t.Fatal(err)
		}
	}
	inForce, err := install.DefaultInForcePath()
	if err != nil {
		t.Fatal(err)
	}
	home, err := os.UserHomeDir()
	if err != nil {
		t.Fatal(err)
	}
	e.opts = install.Options{
		Home: home, ConfigHome: os.Getenv("XDG_CONFIG_HOME"), ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR"),
		GOOS: "linux", Shell: "/bin/bash", ObaPath: oba, ManagedDir: e.managed, InForcePath: inForce,
	}
	e.installDropIn(t, oba)
	if err := install.ApplyGitHooks(e.opts, install.GitHookSettings(oba)); err != nil {
		t.Fatal(err)
	}
	plan, err := install.Build(e.opts)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := install.Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}

	_, devKey, _ := strings.Cut(os.Getenv(delivery.EnvDevTrustedKeys), "=")
	e.keys = []client.SigningKey{{KeyID: "dev", PublicKey: devKey}}

	previousDir, previousLstat, previousKeys, previousVersion := doctorManagedDir, doctorLstat, doctorSigningKeys, doctorVersion
	t.Cleanup(func() {
		doctorManagedDir, doctorLstat, doctorSigningKeys, doctorVersion = previousDir, previousLstat, previousKeys, previousVersion
	})
	doctorManagedDir = func() (string, error) { return e.managed, nil }
	doctorLstat = func(path string) (fs.FileInfo, error) {
		info, err := os.Lstat(path)
		if err != nil {
			return nil, err
		}
		mode := info.Mode()
		if perm, ok := e.mode[path]; ok {
			mode = mode&^fs.ModePerm | perm
		}
		return fakeInfo{FileInfo: info, uid: e.uid[path], mode: mode}, nil
	}
	doctorSigningKeys = func(context.Context) ([]client.SigningKey, error) { return e.keys, e.keysErr }
	doctorVersion = func(*cobra.Command) string { return obaVersionUnderTest }
	return e
}

// setupDelivered prepares the laptop and syncs release 1 into the checkout.
func setupDelivered(t *testing.T) *doctorEnv {
	t.Helper()
	e := setupDoctor(t)
	if out, err := run(t, "", "sync"); err != nil {
		t.Fatalf("sync: %s, %v", out, err)
	}
	return e
}

// installDropIn installs the drop-in this oba renders for obaPath, mode 0644, and proves it in
// force.
func (e *doctorEnv) installDropIn(t *testing.T, obaPath string) {
	t.Helper()
	content, err := install.RenderManaged(obaPath)
	if err != nil {
		t.Fatal(err)
	}
	path := install.DropInPath(e.managed)
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Chmod(path, 0o644); err != nil { // #nosec G302 -- Claude Code reads the drop-in as the user
		t.Fatal(err)
	}
	e.dropIn = content
	if err := install.SaveInForce(e.opts.InForcePath, install.InForceProof{DropInSha256: install.DropInSha256(content), ProvedAt: time.Now()}); err != nil {
		t.Fatal(err)
	}
}

func (e *doctorEnv) requireConfigHooks(t *testing.T) {
	t.Helper()
	if e.oldGit {
		t.Skip("this git runs no hooks from the git config; git 2.54 or newer does")
	}
}

// doctor runs oba kaya doctor --json with args and returns its report.
func (e *doctorEnv) doctor(t *testing.T, args ...string) (doctorReport, error) {
	t.Helper()
	out, err := run(t, "", append([]string{"doctor", "--json"}, args...)...)
	var report doctorReport
	if jsonErr := json.Unmarshal([]byte(out), &report); jsonErr != nil {
		t.Fatalf("doctor --json printed no report (%v, %v):\n%s", jsonErr, err, out)
	}
	return report, err
}

// wantFailed checks that exactly the checks named failed, and the exit code that follows. On a
// git older than 2.54 the two git hook checks fail on every laptop and are left out.
func (e *doctorEnv) wantFailed(t *testing.T, report doctorReport, err error, want ...string) {
	t.Helper()
	gitChecks := func(name string) bool { return e.oldGit && (name == "git hook entries" || name == "git hooks") }
	var failed []string
	for _, check := range report.Checks {
		if check.Status == checkFailed && !gitChecks(check.Name) {
			failed = append(failed, check.Name)
		}
	}
	want = slices.DeleteFunc(slices.Clone(want), gitChecks)
	slices.Sort(failed)
	slices.Sort(want)
	if !slices.Equal(failed, want) {
		t.Fatalf("failed checks = %q, want %q\n%s", failed, want, describe(report))
	}
	if code := exitCodeOf(err); (len(want) > 0 || !e.oldGit) && code != min(len(want), 1) {
		t.Fatalf("exit code = %d (%v), want %d\n%s", code, err, min(len(want), 1), describe(report))
	}
	if report.Passed != (err == nil) {
		t.Fatalf("passed = %v with exit error %v", report.Passed, err)
	}
}

func exitCodeOf(err error) int {
	var exitErr *exitcode.Error
	switch {
	case err == nil:
		return 0
	case errors.As(err, &exitErr):
		return exitErr.Code
	}
	return -1
}

func describe(report doctorReport) string {
	var b strings.Builder
	for _, check := range report.Checks {
		fmt.Fprintf(&b, "  %s/%s %s: %s\n", check.Scope, check.Name, check.Status, check.Detail)
	}
	return b.String()
}

// checkNamed is the check of report with name, failing the test when there is none.
func checkNamed(t *testing.T, report doctorReport, name string) doctorCheck {
	t.Helper()
	for _, check := range report.Checks {
		if check.Name == name {
			return check
		}
	}
	t.Fatalf("no check %q in\n%s", name, describe(report))
	return doctorCheck{}
}

func gitOlderThanConfigHooks(t *testing.T) bool {
	t.Helper()
	out, err := exec.Command("git", "--version").Output()
	if err != nil {
		t.Fatalf("git --version: %v", err)
	}
	match := regexp.MustCompile(`^git version (\d+)\.(\d+)`).FindStringSubmatch(string(out))
	if match == nil {
		t.Fatalf("unreadable git version %q", out)
	}
	major, errMajor := strconv.Atoi(match[1])
	minor, errMinor := strconv.Atoi(match[2])
	if err := errors.Join(errMajor, errMinor); err != nil {
		t.Fatal(err)
	}
	return major < 2 || major == 2 && minor < 54
}

func writeExecutable(t *testing.T, dir, name, script string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(script), 0o700); err != nil { // #nosec G306 -- a stand-in the test runs
		t.Fatal(err)
	}
	return path
}

func writeTestFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func TestDoctor_passesOnALaptopSetUpForKaya(t *testing.T) {
	e := setupDelivered(t)
	report, err := e.doctor(t)
	e.wantFailed(t, report, err)
	if report.Checkout == nil || report.Checkout.Kind != "Delivered" || report.Checkout.Remote != kayatest.Remote || report.Checkout.RemoteName != "origin" {
		t.Fatalf("checkout = %+v", report.Checkout)
	}
	for _, name := range []string{"drop-in", "drop-in owner", "drop-in content", "drop-in in force", "claude function", "machine token",
		"repository list", "signing key", "user settings", "recognition", "release", "current release", "placed", "exclude block",
		"tracked paths", "instruction files"} {
		if check := checkNamed(t, report, name); check.Status != checkOK {
			t.Fatalf("%s: %+v", name, check)
		}
	}
	if !e.oldGit {
		out, err := run(t, "", "doctor")
		if err != nil || !strings.Contains(out, "Machine:\n") || !strings.Contains(out, "  ok   drop-in in force: ") || !strings.HasSuffix(out, "No check failed.\n") {
			t.Fatalf("doctor: %s, %v", out, err)
		}
	}
}

func TestDoctor_namesAMissingDropIn(t *testing.T) {
	e := setupDelivered(t)
	if err := os.Remove(install.DropInPath(e.managed)); err != nil {
		t.Fatal(err)
	}
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "drop-in")
	if check := checkNamed(t, report, "drop-in"); !strings.Contains(check.Detail, "oba kaya install --managed") {
		t.Fatalf("drop-in: %+v", check)
	}
	for _, name := range []string{"drop-in owner", "drop-in content", "drop-in in force"} {
		if check := checkNamed(t, report, name); check.Status != checkSkipped {
			t.Fatalf("%s: %+v", name, check)
		}
	}
}

func TestDoctor_namesADropInThatRunsAnObaOfAnotherVersion(t *testing.T) {
	e := setupDelivered(t)
	t.Setenv("FAKE_OBA_VERSION", "1.3.0")
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "drop-in")
	if check := checkNamed(t, report, "drop-in"); !strings.Contains(check.Detail, "version 1.3.0") || !strings.Contains(check.Detail, obaVersionUnderTest) {
		t.Fatalf("drop-in: %+v", check)
	}
}

func TestDoctor_namesADropInNotOwnedByRootOrWritableByOthers(t *testing.T) {
	for name, change := range map[string]func(e *doctorEnv) (string, string){
		"drop-in owned by the user": func(e *doctorEnv) (string, string) {
			path := install.DropInPath(e.managed)
			e.uid[path] = 501
			return path, "owned by user id 501"
		},
		"drop-in writable by all": func(e *doctorEnv) (string, string) {
			path := install.DropInPath(e.managed)
			e.mode[path] = 0o666
			return path, "mode 0666, not 0644"
		},
		"folder writable by the group": func(e *doctorEnv) (string, string) {
			e.mode[e.managed] = 0o775
			return e.managed, "writable by group or others"
		},
		"parent folder owned by the user": func(e *doctorEnv) (string, string) {
			parent := filepath.Dir(e.managed)
			e.uid[parent] = 501
			return parent, "owned by user id 501"
		},
	} {
		t.Run(name, func(t *testing.T) {
			e := setupDelivered(t)
			path, want := change(e)
			report, err := e.doctor(t)
			e.wantFailed(t, report, err, "drop-in owner")
			if check := checkNamed(t, report, "drop-in owner"); !strings.Contains(check.Detail, path) || !strings.Contains(check.Detail, want) {
				t.Fatalf("drop-in owner: %+v, want %s and %s", check, path, want)
			}
		})
	}
}

func TestDoctor_namesADropInThatIsNotWhatThisObaRenders(t *testing.T) {
	e := setupDelivered(t)
	other := writeExecutable(t, t.TempDir(), "oba", fakeOba)
	e.installDropIn(t, other)
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "drop-in content")
	if check := checkNamed(t, report, "drop-in content"); !strings.Contains(check.Detail, "oba kaya install --managed") {
		t.Fatalf("drop-in content: %+v", check)
	}
}

func TestDoctor_needsAProofThatNamesTheInstalledDropIn(t *testing.T) {
	e := setupDelivered(t)
	if err := install.RemoveInForce(e.opts.InForcePath); err != nil {
		t.Fatal(err)
	}
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "drop-in in force")
	if check := checkNamed(t, report, "drop-in in force"); !strings.Contains(check.Detail, "oba kaya doctor --probe") {
		t.Fatalf("drop-in in force: %+v", check)
	}

	stale := install.InForceProof{DropInSha256: install.DropInSha256([]byte("an earlier drop-in\n")), ProvedAt: time.Now()}
	if err := install.SaveInForce(e.opts.InForcePath, stale); err != nil {
		t.Fatal(err)
	}
	report, err = e.doctor(t)
	e.wantFailed(t, report, err, "drop-in in force")
	if check := checkNamed(t, report, "drop-in in force"); !strings.Contains(check.Detail, "another drop-in") {
		t.Fatalf("drop-in in force: %+v", check)
	}
}

func TestDoctor_namesAGitHookEntryThatDoesNotRunThisOba(t *testing.T) {
	e := setupDelivered(t)
	e.requireConfigHooks(t)
	if out, err := exec.Command("git", "config", "--global", "hook.kaya-pre-commit.command", "/usr/local/bin/oba kaya git-hook pre-commit").CombinedOutput(); err != nil {
		t.Fatalf("%v: %s", err, out)
	}
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "git hook entries")
	if check := checkNamed(t, report, "git hook entries"); !strings.Contains(check.Detail, "hook.kaya-pre-commit.command") || strings.Contains(check.Detail, "hook.kaya-pre-commit.event") {
		t.Fatalf("git hook entries: %+v", check)
	}
}

func TestDoctor_namesAMissingClaudeFunction(t *testing.T) {
	e := setupDelivered(t)
	if err := os.Remove(filepath.Join(e.opts.Home, ".bashrc")); err != nil {
		t.Fatal(err)
	}
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "claude function")
	if check := checkNamed(t, report, "claude function"); !strings.Contains(check.Detail, filepath.Join(e.opts.Home, ".bashrc")) {
		t.Fatalf("claude function: %+v", check)
	}
}

func TestDoctor_namesTheMachineTokenKayaRefusesOrThatIsMissing(t *testing.T) {
	e := setupDelivered(t)
	t.Setenv(machine.EnvToken, "kyt_revoked")
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "machine token", "repository list")
	if check := checkNamed(t, report, "machine token"); !strings.Contains(check.Detail, "refused the machine token") {
		t.Fatalf("machine token: %+v", check)
	}
	if strings.Contains(describe(report), "kyt_revoked") {
		t.Fatalf("doctor printed the machine token:\n%s", describe(report))
	}

	if _, err := exec.LookPath("kagi"); err == nil {
		t.Skip("kagi is on PATH, which would read a real machine token")
	}
	t.Setenv(machine.EnvToken, "")
	report, err = e.doctor(t)
	e.wantFailed(t, report, err, "machine token", "repository list")
	if check := checkNamed(t, report, "machine token"); !strings.Contains(check.Detail, machine.EnvToken) {
		t.Fatalf("machine token: %+v", check)
	}
}

func TestDoctor_withoutACachedListOrNetworkNamesTheCause(t *testing.T) {
	e := setupDoctor(t)
	path, err := delivery.RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	closed := httptest.NewServer(nil)
	closed.Close()
	t.Setenv(config.EnvAPIURL, closed.URL)
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "repository list")
	if check := checkNamed(t, report, "repository list"); !strings.Contains(check.Detail, "not refreshed") || !strings.Contains(check.Detail, "fetched never") {
		t.Fatalf("repository list: %+v", check)
	}
	if report.Checkout == nil || report.Checkout.Kind != "NotKaya" || checkNamed(t, report, "recognition").Status != checkSkipped {
		t.Fatalf("checkout = %+v\n%s", report.Checkout, describe(report))
	}
}

func TestDoctor_namesAnEmptyRepositoryList(t *testing.T) {
	e := setupDoctor(t)
	e.kaya.Listed = nil
	kayatest.ListRepositories(t, kayatest.Organization)
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "repository list")
	if check := checkNamed(t, report, "repository list"); !strings.Contains(check.Detail, "lists no repository") {
		t.Fatalf("repository list: %+v", check)
	}
}

func TestDoctor_checksThatKayaSignsWithAKeyThisObaTrusts(t *testing.T) {
	e := setupDelivered(t)
	devKey := e.keys[0].PublicKey
	for name, keys := range map[string][]client.SigningKey{
		"an unknown key id":                   {{KeyID: "elsewhere-2026", PublicKey: devKey}},
		"a known key id with another key":     {{KeyID: "oba-2026-11", PublicKey: devKey}},
		"no key":                              {},
		"the trusted key id with another key": {{KeyID: "dev", PublicKey: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}},
	} {
		e.keys = keys
		report, err := e.doctor(t)
		e.wantFailed(t, report, err, "signing key")
		if check := checkNamed(t, report, "signing key"); !strings.Contains(check.Detail, "refuses every release") {
			t.Fatalf("%s: %+v", name, check)
		}
	}

	e.keys = []client.SigningKey{{KeyID: "elsewhere-2026", PublicKey: devKey}, {KeyID: "dev", PublicKey: devKey}}
	report, err := e.doctor(t)
	e.wantFailed(t, report, err)
	if check := checkNamed(t, report, "signing key"); check.Status != checkOK || !strings.Contains(check.Detail, "key dev") {
		t.Fatalf("signing key: %+v", check)
	}

	e.keys, e.keysErr = nil, auth.ErrNotLoggedIn
	report, err = e.doctor(t)
	e.wantFailed(t, report, err)
	if check := checkNamed(t, report, "signing key"); check.Status != checkSkipped || !strings.Contains(check.Detail, "oba login") {
		t.Fatalf("signing key without a sign-in: %+v", check)
	}
}

func TestDoctor_namesKayaHooksLeftInTheUserSettingsOnceTheDropInIsInForce(t *testing.T) {
	e := setupDelivered(t)
	writeTestFile(t, install.ClaudeSettingsPath(e.opts), `{"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "`+e.opts.ObaPath+` kaya hook record"}]}]}}`)
	report, err := e.doctor(t)
	e.wantFailed(t, report, err, "user settings")
	if check := checkNamed(t, report, "user settings"); !strings.Contains(check.Detail, "run oba kaya install") {
		t.Fatalf("user settings: %+v", check)
	}

	if err := install.RemoveInForce(e.opts.InForcePath); err != nil {
		t.Fatal(err)
	}
	report, err = e.doctor(t)
	e.wantFailed(t, report, err, "drop-in in force")
	if check := checkNamed(t, report, "user settings"); check.Status != checkSkipped {
		t.Fatalf("user settings without a proof: %+v", check)
	}
}

func TestDoctor_namesEachCheckoutProblemOnItsOwn(t *testing.T) {
	for name, tc := range map[string]struct {
		change func(t *testing.T, e *doctorEnv)
		check  string
		detail string
		git    bool
	}{
		"a damaged release state": {
			change: func(t *testing.T, e *doctorEnv) {
				writeTestFile(t, filepath.Join(e.root, ".kaya", "state.json"), "{")
			},
			check: "release", detail: "oba kaya sync",
		},
		"a newer current release": {
			change: func(t *testing.T, e *doctorEnv) {
				e.kaya.Publish(t, 2, map[string]string{"AGENTS.md": "# Rules v2\n"})
			},
			check: "current release", detail: "Kaya's current one is 2",
		},
		"a removed delivered file": {
			change: func(t *testing.T, e *doctorEnv) {
				if err := os.Remove(filepath.Join(e.root, "AGENTS.md")); err != nil {
					t.Fatal(err)
				}
			},
			check: "placed", detail: "AGENTS.md",
		},
		"an exclude block without a delivered path": {
			change: func(t *testing.T, e *doctorEnv) {
				path := filepath.Join(e.root, ".git", "info", "exclude")
				data, err := os.ReadFile(path) // #nosec G304 -- the test checkout's own exclude file
				if err != nil {
					t.Fatal(err)
				}
				kept := strings.ReplaceAll(string(data), "/AGENTS.md\n", "")
				if kept == string(data) {
					t.Fatalf("no /AGENTS.md line in\n%s", data)
				}
				writeTestFile(t, path, kept)
			},
			check: "exclude block", detail: "/AGENTS.md",
		},
		"a tracked delivered path": {
			change: func(t *testing.T, e *doctorEnv) {
				kayatest.Git(t, e.root, "add", "-f", "AGENTS.md")
			},
			check: "tracked paths", detail: "git rm -r --cached -- AGENTS.md",
		},
		"a git hook switched off in the checkout": {
			change: func(t *testing.T, e *doctorEnv) {
				kayatest.Git(t, e.root, "config", "hook.kaya-pre-commit.enabled", "false")
			},
			check: "git hooks", detail: "no kaya-pre-commit hook", git: true,
		},
		"a checkout in a session's scratchpad": {
			change: func(t *testing.T, e *doctorEnv) {
				moved := filepath.Join(t.TempDir(), "0f8fad5b-d9cb-469f-a165-70867728950e", "scratchpad", "demo")
				if err := os.MkdirAll(filepath.Dir(moved), 0o750); err != nil {
					t.Fatal(err)
				}
				if err := os.Rename(e.root, moved); err != nil {
					t.Fatal(err)
				}
				if err := os.Chdir(moved); err != nil {
					t.Fatal(err)
				}
			},
			check: "instruction files", detail: "scratchpad",
		},
	} {
		t.Run(name, func(t *testing.T) {
			e := setupDelivered(t)
			if tc.git {
				e.requireConfigHooks(t)
			}
			tc.change(t, e)
			report, err := e.doctor(t)
			e.wantFailed(t, report, err, tc.check)
			if check := checkNamed(t, report, tc.check); !strings.Contains(check.Detail, tc.detail) {
				t.Fatalf("%s: %+v, want %q", tc.check, check, tc.detail)
			}
		})
	}
}

func TestDoctor_aCheckoutKayaDoesNotListRunsOnlyTheMachineChecks(t *testing.T) {
	e := setupDelivered(t)
	kayatest.Git(t, e.root, "remote", "set-url", "origin", "git@github.com:example/unlisted.git")
	report, err := e.doctor(t)
	e.wantFailed(t, report, err)
	if check := checkNamed(t, report, "recognition"); check.Status != checkSkipped || !strings.HasPrefix(check.Detail, "NotKaya: ") {
		t.Fatalf("recognition: %+v", check)
	}
	for _, check := range report.Checks {
		if check.Scope == scopeCheckout && check.Name != "recognition" {
			t.Fatalf("a checkout check ran in a checkout Kaya does not list: %+v", check)
		}
	}
}

func TestDoctor_listsWhatTheCutoverDeletesInACheckoutNotCutOver(t *testing.T) {
	e := setupDoctor(t)
	e.kaya.Publish(t, 1, map[string]string{"AGENTS.md": "# Rules\n", "frontend/AGENTS.md": "# Frontend\n"})
	tracked := []string{
		".agents/.managed.json", ".agents/rules/style.md", ".claude/settings.json", ".claude/skills/demo/SKILL.md",
		".github/workflows/agents-sync.yml", ".kaya/notes.md", "AGENTS.md", "frontend/AGENTS.md", "kaya.yaml",
	}
	for _, path := range append(slices.Clone(tracked), "README.md", ".github/workflows/ci.yml", "frontend/app.ts") {
		writeTestFile(t, filepath.Join(e.root, filepath.FromSlash(path)), "content of "+path+"\n")
		kayatest.Git(t, e.root, "add", "--", path)
	}
	for _, path := range []string{".claude/settings.local.json", ".agents/memory/lessons.md", "docs/AGENTS.md", ".kaya/state.json"} {
		writeTestFile(t, filepath.Join(e.root, filepath.FromSlash(path)), "untracked\n")
	}

	report, err := e.doctor(t)
	e.wantFailed(t, report, err)
	if report.Checkout == nil || report.Checkout.Kind != "NotCutOver" || report.Cutover == nil {
		t.Fatalf("checkout = %+v, cutover = %+v", report.Checkout, report.Cutover)
	}
	if !slices.Equal(report.Cutover.Paths, tracked) || report.Cutover.Incomplete != "" {
		t.Fatalf("cutover paths = %q (%s), want %q", report.Cutover.Paths, report.Cutover.Incomplete, tracked)
	}
	const command = "git rm -r -- .agents .claude .github/workflows/agents-sync.yml .kaya AGENTS.md frontend/AGENTS.md kaya.yaml"
	if report.Cutover.Command != command {
		t.Fatalf("cutover command = %q, want %q", report.Cutover.Command, command)
	}
	for _, name := range []string{"release", "placed", "git hooks"} {
		for _, check := range report.Checks {
			if check.Name == name {
				t.Fatalf("a delivery check ran in a checkout not cut over: %+v", check)
			}
		}
	}

	out, err := run(t, "", "doctor")
	if (err != nil) != e.oldGit || !strings.Contains(out, "the cutover pull request deletes these 9 tracked paths:\n  .agents/.managed.json\n") ||
		!strings.Contains(out, "\n  "+command+"\n") {
		t.Fatalf("doctor: %s, %v", out, err)
	}
}

func TestCutoverCommand_quotesForTheShellAndRemovesKayasFoldersWhole(t *testing.T) {
	paths := []string{".agents/a.md", ".agents/b c.md", ".claude/x", "docs/it's AGENTS.md", "AGENTS.md"}
	if got := cutoverCommand(paths, false); got != `git rm -r -- .agents .claude 'docs/it'\''s AGENTS.md' AGENTS.md` {
		t.Fatalf("cutoverCommand = %s", got)
	}
	if got := cutoverCommand([]string{"AGENTS.md"}, true); got != "git rm -r --cached -- AGENTS.md" {
		t.Fatalf("cutoverCommand cached = %s", got)
	}
}

func TestObaOfCommand_readsTheQuotedObaPath(t *testing.T) {
	for command, want := range map[string]string{
		"/opt/homebrew/bin/oba kaya statusline":             "/opt/homebrew/bin/oba",
		`'/Users/a b/bin/oba' kaya statusline`:              "/Users/a b/bin/oba",
		`'/Users/it'\''s/bin/oba' kaya statusline`:          "/Users/it's/bin/oba",
		"oba kaya statusline":                               "",
		"/opt/homebrew/bin/oba kaya statusline --something": "",
	} {
		got, ok := obaOfCommand(command, " kaya statusline")
		if (want != "") != ok || (ok && got != want) {
			t.Fatalf("obaOfCommand(%q) = %q, %v; want %q", command, got, ok, want)
		}
	}
}

func TestSkippedInstructionFolder_namesAScratchpadBelowASessionID(t *testing.T) {
	for path, want := range map[string]string{
		"/tmp/claude-501/x/0f8fad5b-d9cb-469f-a165-70867728950e/scratchpad/repo": "/tmp/claude-501/x/0f8fad5b-d9cb-469f-a165-70867728950e/scratchpad",
		"/work/scratchpad/repo":                      "",
		"/work/repo/.artifact-work/page":             "/work/repo/.artifact-work",
		"/Users/someone/src/github.com/example/demo": "",
	} {
		if got := skippedInstructionFolder(filepath.FromSlash(path)); got != filepath.FromSlash(want) {
			t.Fatalf("skippedInstructionFolder(%q) = %q, want %q", path, got, want)
		}
	}
}

// probeArgs are the arguments the fake claude was started with, one line per run.
func probeArgs(t *testing.T) []string {
	t.Helper()
	data, err := os.ReadFile(os.Getenv("FAKE_CLAUDE_ARGS"))
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	if err != nil {
		t.Fatal(err)
	}
	return strings.Split(strings.TrimSpace(string(data)), "\n")
}

func TestDoctorProbe_writesTheProofWhenTheDropInRecordsTheSession(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	if err := install.RemoveInForce(e.opts.InForcePath); err != nil {
		t.Fatal(err)
	}
	before := time.Now().Add(-time.Second)
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err)
	for _, name := range []string{"probe", "skills and subagents", "AGENTS.md", "record event", "drop-in in force"} {
		if check := checkNamed(t, report, name); check.Status != checkOK {
			t.Fatalf("%s: %+v", name, check)
		}
	}
	proof, ok, err := install.LoadInForce(e.opts.InForcePath)
	if err != nil || !ok || proof.DropInSha256 != install.DropInSha256(e.dropIn) || proof.ProvedAt.Before(before) {
		t.Fatalf("proof = %+v, %v, %v", proof, ok, err)
	}
	raw, err := os.ReadFile(e.opts.InForcePath)
	if err != nil {
		t.Fatal(err)
	}
	if !regexp.MustCompile(`"dropInSha256": "[0-9a-f]{64}"`).Match(raw) || !regexp.MustCompile(`"provedAt": "\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z"`).Match(raw) {
		t.Fatalf("the proof is not lower-case hex and RFC 3339:\n%s", raw)
	}
	// The probe session first, with the user settings left out; then the AGENTS.md session with every
	// settings source but no tool, no MCP server and one turn, its own session id and its own debug
	// log. The empty value of --tools shows as a second space.
	args := probeArgs(t)
	const shared = "-p " + probePrompt + " --output-format stream-json --verbose "
	const rest = `--session-id \S+ --no-session-persistence --debug-file \S+$`
	probe := regexp.MustCompile(`^` + regexp.QuoteMeta(shared+"--setting-sources project,local ") + rest)
	agentsMD := regexp.MustCompile(`^` + regexp.QuoteMeta(shared+"--tools  --disallowedTools mcp__* --max-turns 1 --strict-mcp-config ") + rest)
	if len(args) != 2 || !probe.MatchString(args[0]) || !agentsMD.MatchString(args[1]) {
		t.Fatalf("claude ran with %q", args)
	}
	for _, flag := range []string{"--session-id", "--debug-file"} {
		if first, second := argValue(t, args[0], flag), argValue(t, args[1], flag); first == second {
			t.Fatalf("both sessions ran with %s %s", flag, first)
		}
	}
}

// argValue is the value that follows flag in a line of probeArgs.
func argValue(t *testing.T, line, flag string) string {
	t.Helper()
	match := regexp.MustCompile(` ` + regexp.QuoteMeta(flag) + ` (\S+)`).FindStringSubmatch(line)
	if match == nil {
		t.Fatalf("no %s in %q", flag, line)
	}
	return match[1]
}

func TestLoadedAgentsMD_findsTheCheckoutAmongThePathsOfALine(t *testing.T) {
	dir := t.TempDir()
	root := filepath.Join(dir, "repo")
	comma := filepath.Join(dir, "old, new")
	for _, path := range []string{filepath.Join(root, "AGENTS.md"), filepath.Join(comma, "AGENTS.md")} {
		writeTestFile(t, path, "# Rules\n")
	}
	link := filepath.Join(dir, "link")
	if err := os.Symlink(root, link); err != nil {
		t.Fatal(err)
	}
	want := filepath.Join(root, "AGENTS.md")
	parent := filepath.Join(dir, "AGENTS.md")
	const loaded = "2026-11-24T10:00:00.000Z [DEBUG] [cc-plugin-agents-md] $.ui.log (to debug): no CLAUDE.md found; AGENTS.md loaded: "
	for name, tc := range map[string]struct {
		log, want string
		found     bool
	}{
		"one path":                       {log: loaded + want, want: want, found: true},
		"two paths, the checkout last":   {log: loaded + parent + ", " + want, want: want, found: true},
		"two paths, the checkout first":  {log: loaded + want + ", " + filepath.Join(root, ".claude", "AGENTS.md"), want: want, found: true},
		"the checkout through a link":    {log: loaded + parent + ", " + filepath.Join(link, "AGENTS.md") + "\n", want: want, found: true},
		"a checkout path with the comma": {log: loaded + filepath.Join(comma, "AGENTS.md"), want: filepath.Join(comma, "AGENTS.md"), found: true},
		"only the folder above":          {log: loaded + parent + ", " + filepath.Join(root+"-other", "AGENTS.md"), want: want},
		"no agents-md line":              {log: "2026-11-24T10:00:00.000Z [DEBUG] Session started\n", want: want},
	} {
		if got := loadedAgentsMD([]byte(tc.log), tc.want); got != tc.found {
			t.Fatalf("%s: loadedAgentsMD(%q, %q) = %v", name, tc.log, tc.want, got)
		}
	}
}

func TestLoadedAgentsMD_takesOnlyThePluginsOwnLine(t *testing.T) {
	want := filepath.Join(t.TempDir(), "AGENTS.md")
	writeTestFile(t, want, "# Rules\n")
	const words = "no CLAUDE.md found; AGENTS.md loaded: "
	plugin := "2026-11-24T10:53:35.815Z [DEBUG] [cc-plugin-agents-md] $.ui.log (to debug): " + words + want
	for name, tc := range map[string]struct {
		log   string
		found bool
	}{
		"the plugin's line":                {log: plugin, found: true},
		"the plugin's line after a hook's": {log: "2026-11-24T10:53:35.715Z [DEBUG] Hook SessionStart:startup stdout: " + words + "/elsewhere/AGENTS.md\n" + plugin + "\n", found: true},
		"a hook's output":                  {log: "2026-11-24T10:53:35.715Z [DEBUG] Hook SessionStart:startup stdout: " + words + want},
		"an MCP server naming the plugin":  {log: `2026-11-24T10:53:35.715Z [DEBUG] MCP server "notes": [cc-plugin-agents-md] ` + words + want},
		"a line Claude Code did not start": {log: "[cc-plugin-agents-md] " + words + want},
		"the words alone":                  {log: "2026-11-24T10:53:35.715Z [DEBUG] AGENTS.md loaded: " + want},
	} {
		if got := loadedAgentsMD([]byte(tc.log), want); got != tc.found {
			t.Fatalf("%s: loadedAgentsMD(%q) = %v", name, tc.log, got)
		}
	}
}

func TestDoctorProbe_leavesTheStrictMCPConfigOutWhereAManagedMCPFileFixesTheServers(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	writeTestFile(t, filepath.Join(filepath.Dir(e.managed), managedMCPFile), "{\"mcpServers\": {}}\n")
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err)
	if check := checkNamed(t, report, "AGENTS.md"); check.Status != checkOK {
		t.Fatalf("AGENTS.md: %+v", check)
	}
	if args := probeArgs(t); len(args) != 2 || strings.Contains(args[1], "--strict-mcp-config") ||
		!strings.Contains(args[1], " --tools  --disallowedTools mcp__* --max-turns 1 --session-id ") {
		t.Fatalf("claude ran with %q", args)
	}
}

// lingeringClaude stands in for a claude that prints its init event and leaves sleep running in
// its process group with its stdout, as a hook or an MCP server that outlives its session would.
// It writes the pid of sleep to FAKE_CLAUDE_CHILD and exits, or waits when FAKE_CLAUDE_WAIT is set.
const lingeringClaude = `#!/bin/sh
printf '{"type":"system","subtype":"init"}\n'
/bin/sleep 300 &
echo $! > "$FAKE_CLAUDE_CHILD.tmp"
/bin/mv "$FAKE_CLAUDE_CHILD.tmp" "$FAKE_CLAUDE_CHILD"
if [ -n "$FAKE_CLAUDE_WAIT" ]; then
  /bin/sleep 300
fi
`

// startLingeringSession runs runSession with lingeringClaude in the background and returns the
// pid of the sleep it left, and the channel runSession's error arrives on.
func startLingeringSession(t *testing.T, ctx context.Context) (int, <-chan error) {
	t.Helper()
	child := filepath.Join(t.TempDir(), "child")
	t.Setenv("FAKE_CLAUDE_CHILD", child)
	claude := writeExecutable(t, t.TempDir(), "claude", lingeringClaude)
	root := t.TempDir()
	done := make(chan error, 1)
	go func() {
		_, err := runSession(ctx, claude, root)
		done <- err
	}()
	deadline := time.Now().Add(10 * time.Second)
	for {
		data, err := os.ReadFile(child)
		if err == nil {
			pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
			if err != nil {
				t.Fatal(err)
			}
			t.Cleanup(func() {
				// rule-10-no-op -- reason: the sleep is usually gone already, which is what the test wants.
				_ = syscall.Kill(pid, syscall.SIGKILL)
			})
			return pid, done
		}
		if time.Now().After(deadline) {
			t.Fatalf("the stand-in claude did not start: %v", err)
		}
		time.Sleep(20 * time.Millisecond)
	}
}

// waitForSession fails the test when runSession has not returned within a time far below the
// sleep the stand-in left running.
func waitForSession(t *testing.T, done <-chan error) {
	t.Helper()
	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("runSession: %v", err)
		}
	case <-time.After(30 * time.Second):
		t.Fatal("runSession did not return")
	}
}

func TestRunSession_killsTheProcessGroupWhenStopped(t *testing.T) {
	t.Setenv("FAKE_CLAUDE_WAIT", "yes")
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	pid, done := startLingeringSession(t, ctx)
	cancel()
	waitForSession(t, done)
	deadline := time.Now().Add(10 * time.Second)
	for !errors.Is(syscall.Kill(pid, 0), syscall.ESRCH) {
		if time.Now().After(deadline) {
			t.Fatalf("process %d of the stopped session's group still runs", pid)
		}
		time.Sleep(20 * time.Millisecond)
	}
}

func TestRunSession_returnsWhileAProcessThatOutlivedClaudeHoldsItsOutput(t *testing.T) {
	_, done := startLingeringSession(t, context.Background())
	waitForSession(t, done)
}

func TestDoctorProbe_checksAGENTSmdInTheSessionWithTheUserSettings(t *testing.T) {
	for name, tc := range map[string]struct {
		agentsMD string
		failed   []string
		detail   string
	}{
		"only that session loads it":      {agentsMD: "user", detail: "with every settings source, as a normal session has, loaded "},
		"only the probe session loads it": {agentsMD: "probe", failed: []string{"AGENTS.md"}, detail: "CLAUDE.local.md"},
		"neither session loads it":        {agentsMD: "no", failed: []string{"AGENTS.md"}, detail: "pluginConfigs"},
		"both sessions load it":           {agentsMD: "probe user", detail: "as a normal session has, loaded "},
	} {
		t.Run(name, func(t *testing.T) {
			e := setupDelivered(t)
			writeExecutable(t, e.bin, "claude", fakeClaude)
			t.Setenv("FAKE_CLAUDE_AGENTS_MD", tc.agentsMD)
			report, err := e.doctor(t, "--probe")
			e.wantFailed(t, report, err, tc.failed...)
			check := checkNamed(t, report, "AGENTS.md")
			if !strings.Contains(check.Detail, tc.detail) || !strings.Contains(check.Detail, filepath.Join(e.root, "AGENTS.md")) ||
				strings.Contains(check.Detail, "setting-sources") {
				t.Fatalf("AGENTS.md: %+v, want %q", check, tc.detail)
			}
		})
	}
}

func TestDoctorProbe_namesASecondSessionThatDoesNotStart(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	t.Setenv("FAKE_CLAUDE_START", "probe")
	if err := install.RemoveInForce(e.opts.InForcePath); err != nil {
		t.Fatal(err)
	}
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err, "AGENTS.md")
	if check := checkNamed(t, report, "AGENTS.md"); !strings.Contains(check.Detail, "started no session") || !strings.Contains(check.Detail, "Invalid API key") {
		t.Fatalf("AGENTS.md: %+v", check)
	}
	if _, ok, err := install.LoadInForce(e.opts.InForcePath); !ok || err != nil {
		t.Fatalf("the probe session alone proves the drop-in in force: %v, %v", ok, err)
	}
}

func TestDoctorProbe_takesTheProofOnlyFromTheProbeSession(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	// A record hook left in the user settings queues the events of the session with the user
	// settings only, as when Claude Code skips the drop-in.
	t.Setenv("FAKE_CLAUDE_RECORD", "user")
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err, "record event", "drop-in in force")
	if check := checkNamed(t, report, "AGENTS.md"); check.Status != checkOK {
		t.Fatalf("AGENTS.md: %+v", check)
	}
	if _, ok, err := install.LoadInForce(e.opts.InForcePath); ok || err != nil {
		t.Fatalf("the proof is still there: %v, %v", ok, err)
	}
	args := probeArgs(t)
	if len(args) != 2 {
		t.Fatalf("claude ran with %q", args)
	}
	stateDir, err := delivery.RecorderStateDir()
	if err != nil {
		t.Fatal(err)
	}
	for i, queued := range []bool{false, true} {
		_, err := os.Stat(filepath.Join(capture.SessionDir(stateDir, argValue(t, args[i], "--session-id")), capture.EventsFile))
		if (err == nil) != queued {
			t.Fatalf("session %d queued an event: %v, want %v", i+1, err == nil, queued)
		}
	}
}

func TestDoctorProbe_runsNoSecondSessionWithoutAnAGENTSmd(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	if err := os.Remove(filepath.Join(e.root, "AGENTS.md")); err != nil {
		t.Fatal(err)
	}
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err, "placed", "AGENTS.md")
	if check := checkNamed(t, report, "AGENTS.md"); !strings.Contains(check.Detail, "no root AGENTS.md") {
		t.Fatalf("AGENTS.md: %+v", check)
	}
	if args := probeArgs(t); len(args) != 1 || !strings.Contains(args[0], " --setting-sources project,local ") {
		t.Fatalf("claude ran with %q", args)
	}
}

func TestDoctorProbe_withoutARecordEventDeletesTheProofAndNamesTheSkip(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	t.Setenv("FAKE_CLAUDE_RECORD", "no")
	settings := install.ClaudeSettingsPath(e.opts)
	writeTestFile(t, settings, `{"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "`+e.opts.ObaPath+` kaya hook record"}]}]}}`)
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err, "record event", "drop-in in force")
	check := checkNamed(t, report, "record event")
	for _, want := range []string{`"first-wins"`, "Skipped sources", "managedSourcesBehavior \"merge\"", "The in-force proof was deleted"} {
		if !strings.Contains(check.Detail, want) {
			t.Fatalf("record event lacks %s: %+v", want, check)
		}
	}
	if _, ok, err := install.LoadInForce(e.opts.InForcePath); ok || err != nil {
		t.Fatalf("the proof is still there: %v, %v", ok, err)
	}
	if check := checkNamed(t, report, "user settings"); check.Status != checkSkipped {
		t.Fatalf("user settings: %+v", check)
	}
	plan, err := install.Build(e.opts)
	if err != nil {
		t.Fatal(err)
	}
	for _, change := range plan.Changes {
		if change.Path == settings {
			t.Fatalf("install would change the user settings without a proof: %s", change.After)
		}
	}
}

func TestDoctorProbe_namesWhatTheSessionDidNotLoad(t *testing.T) {
	e := setupDelivered(t)
	writeExecutable(t, e.bin, "claude", fakeClaude)
	t.Setenv("FAKE_CLAUDE_SKILLS", `["example:other"]`)
	t.Setenv("FAKE_CLAUDE_AGENTS", `["helper"]`)
	t.Setenv("FAKE_CLAUDE_AGENTS_MD", "no")
	if err := install.RemoveInForce(e.opts.InForcePath); err != nil {
		t.Fatal(err)
	}
	report, err := e.doctor(t, "--probe")
	e.wantFailed(t, report, err, "skills and subagents", "AGENTS.md")
	if check := checkNamed(t, report, "skills and subagents"); !strings.Contains(check.Detail, "skill notes") || strings.Contains(check.Detail, "subagent") {
		t.Fatalf("skills and subagents: %+v", check)
	}
	if check := checkNamed(t, report, "AGENTS.md"); !strings.Contains(check.Detail, filepath.Join(e.root, "AGENTS.md")) {
		t.Fatalf("AGENTS.md: %+v", check)
	}
	if _, ok, err := install.LoadInForce(e.opts.InForcePath); !ok || err != nil {
		t.Fatalf("the record event alone proves the drop-in in force: %v, %v", ok, err)
	}
}

func TestDoctorProbe_leavesTheProofAloneWhenItProvesNothing(t *testing.T) {
	for name, tc := range map[string]struct {
		change func(t *testing.T, e *doctorEnv)
		detail string
	}{
		"claude is not installed": {
			change: func(t *testing.T, _ *doctorEnv) {
				if _, err := exec.LookPath("claude"); err == nil {
					t.Skip("a claude outside the test is on PATH")
				}
			},
			detail: "claude was not found on PATH",
		},
		"the session does not start": {
			change: func(t *testing.T, e *doctorEnv) {
				writeExecutable(t, e.bin, "claude", fakeClaude)
				t.Setenv("FAKE_CLAUDE_START", "no")
			},
			detail: "Invalid API key",
		},
		"a project file registers the record hook": {
			change: func(t *testing.T, e *doctorEnv) {
				writeExecutable(t, e.bin, "claude", fakeClaude)
				writeTestFile(t, filepath.Join(e.root, ".claude", "settings.local.json"),
					`{"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "oba kaya hook record"}]}]}}`)
			},
			detail: "settings.local.json registers oba kaya hook record",
		},
		"a project file registers the record hook in another spelling": {
			change: func(t *testing.T, e *doctorEnv) {
				writeExecutable(t, e.bin, "claude", fakeClaude)
				writeTestFile(t, filepath.Join(e.root, ".claude", "settings.json"),
					`{"hooks": {"Stop": [{"hooks": [{"type": "command", "command": "'/opt/my tools/oba'  kaya \"hook\"\trecord"}]}]}}`)
			},
			detail: "settings.json registers oba kaya hook record",
		},
		"a checkout Kaya does not list": {
			change: func(t *testing.T, e *doctorEnv) {
				writeExecutable(t, e.bin, "claude", fakeClaude)
				kayatest.Git(t, e.root, "remote", "set-url", "origin", "git@github.com:example/unlisted.git")
			},
			detail: "in a checkout Kaya recognises",
		},
	} {
		t.Run(name, func(t *testing.T) {
			e := setupDelivered(t)
			tc.change(t, e)
			before, err := os.ReadFile(e.opts.InForcePath)
			if err != nil {
				t.Fatal(err)
			}
			report, err := e.doctor(t, "--probe")
			e.wantFailed(t, report, err, "probe")
			if check := checkNamed(t, report, "probe"); !strings.Contains(check.Detail, tc.detail) || !strings.Contains(check.Detail, "left as it was") {
				t.Fatalf("probe: %+v, want %q", check, tc.detail)
			}
			if after, err := os.ReadFile(e.opts.InForcePath); err != nil || string(after) != string(before) {
				t.Fatalf("the proof changed: %s, %v", after, err)
			}
			if args := probeArgs(t); len(args) > 1 {
				t.Fatalf("a second session ran after a probe that proved nothing: %q", args)
			}
		})
	}
}
