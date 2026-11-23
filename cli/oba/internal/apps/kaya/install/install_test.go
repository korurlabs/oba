package install

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func testOptions(t *testing.T) Options {
	t.Helper()
	home := t.TempDir()
	// git reads and writes a global config of this test only.
	t.Setenv("GIT_CONFIG_GLOBAL", filepath.Join(home, ".gitconfig"))
	t.Setenv("GIT_CONFIG_NOSYSTEM", "1")
	if err := os.MkdirAll(filepath.Join(home, ".config", "Code", "User"), 0o750); err != nil {
		t.Fatal(err)
	}
	return Options{
		Home: home, GOOS: "linux", Shell: "/bin/bash", ObaPath: "/usr/local/bin/oba",
		ManagedDir:  filepath.Join(home, "managed-settings.d"),
		InForcePath: filepath.Join(home, ".config", "oba", "kaya", InForceName),
	}
}

func write(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

// installDropIn puts the drop-in rendered for the oba of opts into the managed settings folder,
// as the printed root step would, and returns its sha256.
func installDropIn(t *testing.T, opts Options) string {
	t.Helper()
	content, err := RenderManaged(opts.ObaPath)
	if err != nil {
		t.Fatal(err)
	}
	write(t, DropInPath(opts.ManagedDir), string(content))
	sum := sha256.Sum256(content)
	return hex.EncodeToString(sum[:])
}

// writeProof writes an in-force proof that names the drop-in with sha.
func writeProof(t *testing.T, opts Options, sha string) {
	t.Helper()
	write(t, opts.InForcePath, `{"dropInSha256":"`+sha+`","provedAt":"2026-11-23T10:00:00Z"}`)
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func apply(t *testing.T, opts Options) Plan {
	t.Helper()
	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}
	return plan
}

func TestInstall_isIdempotentAndBacksUpEveryChangedFile(t *testing.T) {
	opts := testOptions(t)
	vscode := VSCodeSettingsPath(opts)
	claude := ClaudeSettingsPath(opts)
	bashrc := filepath.Join(opts.Home, ".bashrc")
	userSettings := `{"model":"opus","hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"guard.sh"}]}]}}`
	write(t, vscode, "{\n  \"editor.fontSize\": 14\n}\n")
	write(t, claude, userSettings)
	write(t, bashrc, "export EDITOR=vim\n")

	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if len(plan.Changes) != 3 || len(plan.Manual) != 1 || !strings.Contains(plan.Manual[0], "oba kaya install --managed") {
		t.Fatalf("plan = %+v", plan)
	}
	backups, err := Apply(plan.Changes, time.Date(2026, 10, 24, 9, 0, 0, 0, time.UTC))
	if err != nil {
		t.Fatal(err)
	}
	if len(backups) != 2 {
		t.Fatalf("backups = %v", backups)
	}
	original, err := os.ReadFile(bashrc + ".oba-backup-20261024-090000")
	if err != nil || string(original) != "export EDITOR=vim\n" {
		t.Fatalf("backup = %q, %v", original, err)
	}

	link := filepath.Join(opts.Home, ".local", "share", "oba", "bin", LaunchName)
	if target, err := os.Readlink(link); err != nil || target != opts.ObaPath {
		t.Fatalf("oba-kaya-launch links to %q (%v)", target, err)
	}
	settings, err := os.ReadFile(vscode)
	if err != nil || !strings.Contains(string(settings), `"claudeCode.claudeProcessWrapper": "`+link+`"`) || !strings.Contains(string(settings), "editor.fontSize") {
		t.Fatalf("vscode settings = %s", settings)
	}
	if got := readFile(t, claude); got != userSettings {
		t.Fatalf("install wrote into the Claude Code user settings: %s", got)
	}
	rc, err := os.ReadFile(bashrc)
	if err != nil || !strings.HasPrefix(string(rc), "export EDITOR=vim\n") || !strings.Contains(string(rc), `claude() { /usr/local/bin/oba kaya launch claude "$@"; }`) {
		t.Fatalf("bashrc = %s", rc)
	}

	again, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if len(again.Changes) != 0 {
		t.Fatalf("a second install changes %+v", again.Changes)
	}
}

func TestInstall_writesNoUserSettingsWhenNoneExist(t *testing.T) {
	opts := testOptions(t)
	opts.LaunchPath = "/opt/bin/oba-kaya-launch"
	installDropIn(t, opts)
	apply(t, opts)
	if _, err := os.Stat(ClaudeSettingsPath(opts)); !os.IsNotExist(err) {
		t.Fatalf("install created the Claude Code user settings: %v", err)
	}
}

func TestInstall_leavesAVSCodeFileWithCommentsToTheUser(t *testing.T) {
	opts := testOptions(t)
	opts.LaunchPath = "/opt/bin/oba-kaya-launch"
	content := "{\n  // font\n  \"editor.fontSize\": 14,\n}\n"
	write(t, VSCodeSettingsPath(opts), content)

	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	for _, change := range plan.Changes {
		if change.Path == VSCodeSettingsPath(opts) {
			t.Fatal("rewrote a settings file with comments")
		}
	}
	if len(plan.Manual) != 2 || !strings.Contains(plan.Manual[0], WrapperSetting) {
		t.Fatalf("manual = %v", plan.Manual)
	}
}

func TestInstall_refusesAClaudeSettingsFileThatIsNotJSON(t *testing.T) {
	opts := testOptions(t)
	write(t, ClaudeSettingsPath(opts), "{ not json")
	if _, err := Build(opts); err == nil {
		t.Fatal("accepted broken user settings")
	}
}

func TestInstall_refusesARelativeManagedFolderOrProof(t *testing.T) {
	for _, change := range []func(*Options){
		func(o *Options) { o.ManagedDir = "" },
		func(o *Options) { o.ManagedDir = "managed-settings.d" },
		func(o *Options) { o.InForcePath = "" },
	} {
		opts := testOptions(t)
		change(&opts)
		if _, err := Build(opts); err == nil {
			t.Fatalf("accepted the managed folder %q and the proof %q", opts.ManagedDir, opts.InForcePath)
		}
	}
}

func TestInstall_removesAStaleConfigChangeHook(t *testing.T) {
	opts := testOptions(t)
	claude := ClaudeSettingsPath(opts)
	write(t, claude, `{"hooks":{"ConfigChange":[`+
		`{"hooks":[{"type":"command","command":"other.sh"}]},`+
		`{"hooks":[{"type":"command","command":"/old/bin/kaya hook config-change","timeout":5}]}]}}`)
	apply(t, opts)
	data := readFile(t, claude)
	var settings struct {
		Hooks map[string][]hookGroup `json:"hooks"`
	}
	if err := json.Unmarshal([]byte(data), &settings); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(data, "config-change") {
		t.Fatalf("the stale hook is still registered: %s", data)
	}
	if groups := settings.Hooks["ConfigChange"]; len(groups) != 1 || groups[0].Hooks[0].Command != "other.sh" {
		t.Fatalf("ConfigChange = %+v, want only other.sh", groups)
	}
	if len(settings.Hooks) != 1 {
		t.Fatalf("hooks = %+v, want no hook added", settings.Hooks)
	}
}

func TestInstall_dropsTheConfigChangeEventWhenOnlyKayaUsedIt(t *testing.T) {
	opts := testOptions(t)
	claude := ClaudeSettingsPath(opts)
	write(t, claude, `{"hooks":{"ConfigChange":[{"hooks":[{"type":"command","command":"'/old/bin/kaya' hook config-change"}]}]}}`)
	apply(t, opts)
	data := readFile(t, claude)
	if strings.Contains(data, "ConfigChange") || strings.Contains(data, "config-change") {
		t.Fatalf("settings = %s", data)
	}
	again, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if len(again.Changes) != 0 {
		t.Fatalf("a second install changes %+v", again.Changes)
	}
}

func TestInstall_replacesTheClaudeBlockOfTheFormerKayaCLI(t *testing.T) {
	opts := testOptions(t)
	opts.LaunchPath = "/opt/bin/oba-kaya-launch"
	bashrc := filepath.Join(opts.Home, ".bashrc")
	write(t, bashrc, "export EDITOR=vim\n\n# >>> kaya >>>\nclaude() { /usr/local/bin/kaya launch claude \"$@\"; }\n# <<< kaya <<<\nalias ll='ls -l'\n")
	apply(t, opts)
	rc := readFile(t, bashrc)
	if strings.Contains(rc, "# >>> kaya >>>") || strings.Contains(rc, "/usr/local/bin/kaya launch") ||
		!strings.Contains(rc, "alias ll='ls -l'") || strings.Count(rc, "# >>> oba kaya >>>") != 1 {
		t.Fatalf(".bashrc = %s", rc)
	}
}

// kayaUserSettings holds Kaya's hooks of an earlier install, of an older oba path, of the former
// kaya CLI and a record hook, beside the developer's own guard and status line.
const kayaUserSettings = `{"model":"opus","statusLine":{"type":"command","command":"my-line.sh"},"hooks":{` +
	`"SessionStart":[{"hooks":[{"type":"command","command":"/usr/local/bin/oba kaya hook session-start","timeout":10}]}],` +
	`"SessionEnd":[{"hooks":[{"type":"command","command":"/old/bin/oba kaya hook session-end","timeout":5}]}],` +
	`"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"guard.sh"}]},` +
	`{"matcher":"*","hooks":[{"type":"command","command":"'/opt/kaya dir/kaya' hook pre-tool-use","timeout":60}]}],` +
	`"UserPromptSubmit":[{"hooks":[{"type":"command","command":"/usr/local/bin/oba kaya hook user-prompt-submit","timeout":5}]}],` +
	`"PostToolUse":[{"matcher":"*","hooks":[{"type":"command","command":"/usr/local/bin/oba kaya hook post-tool-use","timeout":5}]}],` +
	`"Stop":[{"hooks":[{"type":"command","command":"/usr/local/bin/oba kaya hook record","timeout":5}]}]}}`

func TestInstall_removesKayaUserHooksOnlyWithAProofOfTheInstalledDropIn(t *testing.T) {
	otherSha := strings.Repeat("a", 64)
	tests := []struct {
		name        string
		setup       func(t *testing.T, opts Options)
		wantRemoved bool
		wantManual  string
	}{
		{
			name:       "no drop-in",
			setup:      func(t *testing.T, opts Options) { writeProof(t, opts, otherSha) },
			wantManual: "oba kaya install --managed",
		},
		{
			name:       "a drop-in without a proof",
			setup:      func(t *testing.T, opts Options) { installDropIn(t, opts) },
			wantManual: "no in-force proof",
		},
		{
			name: "a proof of another drop-in",
			setup: func(t *testing.T, opts Options) {
				installDropIn(t, opts)
				writeProof(t, opts, otherSha)
			},
			wantManual: "names another drop-in",
		},
		{
			name: "a proof that is not valid",
			setup: func(t *testing.T, opts Options) {
				installDropIn(t, opts)
				write(t, opts.InForcePath, `{"dropInSha256":"x"`)
			},
			wantManual: "not valid",
		},
		{
			name:        "a proof of the installed drop-in",
			setup:       func(t *testing.T, opts Options) { writeProof(t, opts, installDropIn(t, opts)) },
			wantRemoved: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			opts := testOptions(t)
			opts.LaunchPath = "/opt/bin/oba-kaya-launch"
			claude := ClaudeSettingsPath(opts)
			write(t, claude, kayaUserSettings)
			tt.setup(t, opts)

			plan := apply(t, opts)

			data := readFile(t, claude)
			if !tt.wantRemoved {
				if data != kayaUserSettings {
					t.Fatalf("settings changed without a proof of the installed drop-in: %s", data)
				}
				if len(plan.Manual) != 1 || !strings.Contains(plan.Manual[0], tt.wantManual) {
					t.Fatalf("manual = %v, want %q", plan.Manual, tt.wantManual)
				}
				if tt.wantManual != "oba kaya install --managed" && !strings.Contains(plan.Manual[0], "until then Kaya's hooks stay in "+claude) {
					t.Fatalf("manual = %v, want the kept hooks named", plan.Manual)
				}
				return
			}
			if len(plan.Manual) != 0 {
				t.Fatalf("manual = %v", plan.Manual)
			}
			var settings struct {
				Model      string                 `json:"model"`
				StatusLine statusLine             `json:"statusLine"`
				Hooks      map[string][]hookGroup `json:"hooks"`
			}
			if err := json.Unmarshal([]byte(data), &settings); err != nil {
				t.Fatal(err)
			}
			if strings.Contains(data, "kaya hook") {
				t.Fatalf("a Kaya hook is left in the user settings: %s", data)
			}
			if settings.Model != "opus" || settings.StatusLine.Command != "my-line.sh" || len(settings.Hooks) != 1 ||
				len(settings.Hooks["PreToolUse"]) != 1 || settings.Hooks["PreToolUse"][0].Hooks[0].Command != "guard.sh" {
				t.Fatalf("settings = %s, want the developer's own entries kept", data)
			}
			again, err := Build(opts)
			if err != nil {
				t.Fatal(err)
			}
			if len(again.Changes) != 0 || len(again.Manual) != 0 {
				t.Fatalf("a second install plans %+v", again)
			}
		})
	}
}

func TestInstall_dropsTheHooksKeyWhenOnlyKayaUsedIt(t *testing.T) {
	opts := testOptions(t)
	claude := ClaudeSettingsPath(opts)
	write(t, claude, `{"hooks":{"SessionStart":[{"hooks":[{"type":"command","command":"/usr/local/bin/oba kaya hook session-start","timeout":10}]}]}}`)
	writeProof(t, opts, installDropIn(t, opts))
	apply(t, opts)
	if data := readFile(t, claude); data != "{}\n" {
		t.Fatalf("settings = %q", data)
	}
}

// globalGit is every value of key in the test's global git config.
func globalGit(t *testing.T, key string) []string {
	t.Helper()
	out, err := exec.Command("git", "config", "--global", "--get-all", key).Output()
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) && exitErr.ExitCode() == 1 {
		return nil
	}
	if err != nil {
		t.Fatalf("git config --get-all %s: %v", key, err)
	}
	return strings.Split(strings.TrimSuffix(string(out), "\n"), "\n")
}

func applyGitHooks(t *testing.T, opts Options) GitPlan {
	t.Helper()
	plan, err := BuildGitHooks(opts)
	if err != nil {
		t.Fatal(err)
	}
	if err := ApplyGitHooks(opts, plan.Settings); err != nil {
		t.Fatal(err)
	}
	return plan
}

func TestGitHooks_areWrittenOnceWithTheAbsoluteObaPath(t *testing.T) {
	opts := testOptions(t)
	plan := applyGitHooks(t, opts)
	if len(plan.Settings) != 4 || len(plan.Unchanged) != 0 {
		t.Fatalf("plan = %+v", plan)
	}
	for _, event := range []string{"pre-commit", "post-checkout"} {
		if got := globalGit(t, "hook.kaya-"+event+".event"); len(got) != 1 || got[0] != event {
			t.Fatalf("hook.kaya-%s.event = %q", event, got)
		}
		command := globalGit(t, "hook.kaya-"+event+".command")
		if len(command) != 1 || !strings.HasSuffix(command[0], "; exec /usr/local/bin/oba kaya git-hook "+event) ||
			!strings.HasPrefix(command[0], "[ -x /usr/local/bin/oba ] || ") {
			t.Fatalf("hook.kaya-%s.command = %q", event, command)
		}
	}

	before := readFile(t, os.Getenv("GIT_CONFIG_GLOBAL"))
	again := applyGitHooks(t, opts)
	if len(again.Settings) != 0 || len(again.Unchanged) != 4 {
		t.Fatalf("a second install plans %+v", again)
	}
	if after := readFile(t, os.Getenv("GIT_CONFIG_GLOBAL")); after != before {
		t.Fatalf("a second install changed the global git config:\n%s", after)
	}
}

func TestGitHooks_followAMovedObaAndKeepOneValuePerKey(t *testing.T) {
	opts := testOptions(t)
	applyGitHooks(t, opts)
	if out, err := exec.Command("git", "config", "--global", "--add", "hook.kaya-pre-commit.command", "stale").CombinedOutput(); err != nil {
		t.Fatalf("%v: %s", err, out)
	}
	opts.ObaPath = "/opt/homebrew/bin/oba"
	plan := applyGitHooks(t, opts)
	if len(plan.Settings) != 2 {
		t.Fatalf("plan = %+v", plan)
	}
	for _, event := range []string{"pre-commit", "post-checkout"} {
		if got := globalGit(t, "hook.kaya-"+event+".command"); len(got) != 1 || got[0] != GitHookCommand("/opt/homebrew/bin/oba", event) {
			t.Fatalf("hook.kaya-%s.command = %q", event, got)
		}
	}
}

func TestGitHooks_refuseARelativeObaPath(t *testing.T) {
	opts := testOptions(t)
	opts.ObaPath = "oba"
	if _, err := BuildGitHooks(opts); err == nil || !strings.Contains(err.Error(), "not absolute") {
		t.Fatalf("err = %v", err)
	}
}

// gitRefusingToWrite puts a git first on PATH that refuses to write key, as git does while another
// program holds the lock of the global git config, and passes every other call to the real git.
func gitRefusingToWrite(t *testing.T, key string) {
	t.Helper()
	realGit, err := exec.LookPath("git")
	if err != nil {
		t.Fatal(err)
	}
	dir := t.TempDir()
	fake := filepath.Join(dir, "git")
	write(t, fake, "#!/bin/sh\ncase \" $* \" in\n*\" --replace-all "+key+" \"*)\n"+
		"\techo 'error: could not lock config file' >&2\n\texit 255\n\t;;\nesac\nexec "+shellQuote(realGit)+" \"$@\"\n")
	if err := os.Chmod(fake, 0o700); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
}

func TestGitHooks_aWriteThatFailsLeavesGitWorking(t *testing.T) {
	realGit, err := exec.LookPath("git")
	if err != nil {
		t.Fatal(err)
	}
	for _, failing := range GitHookSettings("/usr/local/bin/oba") {
		t.Run(failing.Key, func(t *testing.T) {
			opts := testOptions(t)
			t.Setenv("HOME", opts.Home)
			t.Setenv("XDG_CONFIG_HOME", filepath.Join(opts.Home, ".config"))
			gitRefusingToWrite(t, failing.Key)

			err := ApplyGitHooks(opts, GitHookSettings(opts.ObaPath))
			if err == nil || !strings.Contains(err.Error(), "could not lock config file") {
				t.Fatalf("err = %v", err)
			}
			for _, event := range GitHookEvents {
				name := "hook." + GitHookName(event)
				if len(globalGit(t, name+".event")) != 0 && len(globalGit(t, name+".command")) == 0 {
					t.Fatalf("the global git config sets %s.event without %s.command", name, name)
				}
			}
			repo := t.TempDir()
			for _, args := range [][]string{{"init", "--quiet", repo}, {"-C", repo, "status", "--short"}} {
				if out, err := exec.Command(realGit, args...).CombinedOutput(); err != nil {
					t.Fatalf("git %s: %v\n%s", strings.Join(args, " "), err, out)
				}
			}
		})
	}
}

// runHookCommand runs command as git runs a hook from the git config: with sh, the hook's
// arguments appended.
func runHookCommand(t *testing.T, command string, args ...string) (string, error) {
	t.Helper()
	var stderr bytes.Buffer
	cmd := exec.Command("sh", append([]string{"-c", command + ` "$@"`, command}, args...)...)
	cmd.Stderr = &stderr
	err := cmd.Run()
	return stderr.String(), err
}

func TestGitHookCommand_passesGitsArgumentsAndLetsGitGoOnWithoutOba(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "a b's bin")
	oba := filepath.Join(dir, "oba")
	record := filepath.Join(t.TempDir(), "args")
	write(t, oba, "#!/bin/sh\nprintf '%s|' \"$@\" > '"+record+"'\nexit 1\n")
	if err := os.Chmod(oba, 0o700); err != nil {
		t.Fatal(err)
	}
	command := GitHookCommand(oba, "post-checkout")

	if _, err := runHookCommand(t, command, "abc", "def", "1"); err == nil {
		t.Fatal("the exit status of oba was not passed on")
	}
	if got := readFile(t, record); got != "kaya|git-hook|post-checkout|abc|def|1|" {
		t.Fatalf("oba got %q", got)
	}

	if err := os.Remove(oba); err != nil {
		t.Fatal(err)
	}
	stderr, err := runHookCommand(t, command, "abc", "def", "1")
	if err != nil {
		t.Fatalf("a missing oba failed the hook: %v", err)
	}
	if stderr != "oba kaya: "+oba+" is missing, so the Kaya git hook post-checkout did not run; run oba kaya install\n" {
		t.Fatalf("stderr = %q", stderr)
	}
}

func TestGitVersionAdvice(t *testing.T) {
	for version, want := range map[string]string{
		"git version 2.54.0 (Apple Git-157)\n": "",
		"git version 2.60.1":                   "",
		"git version 3.0.0":                    "",
		"git version 2.53.2":                   "this is git 2.53, so update git",
		"git version 1.99.0":                   "this is git 1.99, so update git",
		"not git":                              `could not be read from "not git"`,
	} {
		got := gitVersionAdvice(version)
		if want == "" && got != "" || want != "" && !strings.Contains(got, want) {
			t.Errorf("gitVersionAdvice(%q) = %q, want %q", version, got, want)
		}
		if want != "" && !strings.Contains(got, "need git 2.54 or newer") {
			t.Errorf("gitVersionAdvice(%q) = %q names no minimum", version, got)
		}
	}
}
