package install

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func testOptions(t *testing.T) Options {
	t.Helper()
	home := t.TempDir()
	if err := os.MkdirAll(filepath.Join(home, ".config", "Code", "User"), 0o750); err != nil {
		t.Fatal(err)
	}
	return Options{Home: home, GOOS: "linux", Shell: "/bin/bash", KayaPath: "/usr/local/bin/kaya"}
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

func TestInstall_isIdempotentAndBacksUpEveryChangedFile(t *testing.T) {
	opts := testOptions(t)
	vscode := VSCodeSettingsPath(opts)
	claude := ClaudeSettingsPath(opts)
	bashrc := filepath.Join(opts.Home, ".bashrc")
	write(t, vscode, "{\n  \"editor.fontSize\": 14\n}\n")
	write(t, claude, `{"model":"opus","hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"guard.sh"}]}]}}`)
	write(t, bashrc, "export EDITOR=vim\n")

	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if len(plan.Changes) != 4 || len(plan.Manual) != 0 {
		t.Fatalf("plan = %+v", plan)
	}
	backups, err := Apply(plan.Changes, time.Date(2026, 10, 24, 9, 0, 0, 0, time.UTC))
	if err != nil {
		t.Fatal(err)
	}
	if len(backups) != 3 {
		t.Fatalf("backups = %v", backups)
	}
	original, err := os.ReadFile(bashrc + ".kaya-backup-20261024-090000")
	if err != nil || string(original) != "export EDITOR=vim\n" {
		t.Fatalf("backup = %q, %v", original, err)
	}

	link := filepath.Join(opts.Home, ".local", "share", "kaya", "bin", LaunchName)
	if target, err := os.Readlink(link); err != nil || target != opts.KayaPath {
		t.Fatalf("kaya-launch links to %q (%v)", target, err)
	}
	settings, err := os.ReadFile(vscode)
	if err != nil || !strings.Contains(string(settings), `"claudeCode.claudeProcessWrapper": "`+link+`"`) || !strings.Contains(string(settings), "editor.fontSize") {
		t.Fatalf("vscode settings = %s", settings)
	}
	hooks, err := os.ReadFile(claude)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"guard.sh", `"model": "opus"`, "/usr/local/bin/kaya hook session-start", "hook session-end", "hook pre-tool-use", "hook user-prompt-submit", "hook post-tool-use"} {
		if !strings.Contains(string(hooks), want) {
			t.Fatalf("claude settings miss %q: %s", want, hooks)
		}
	}
	rc, err := os.ReadFile(bashrc)
	if err != nil || !strings.HasPrefix(string(rc), "export EDITOR=vim\n") || !strings.Contains(string(rc), `claude() { /usr/local/bin/kaya launch claude "$@"; }`) {
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

func TestInstall_replacesTheHooksOfAnEarlierKayaPath(t *testing.T) {
	opts := testOptions(t)
	opts.LaunchPath = "/opt/bin/kaya-launch"
	old := opts
	old.KayaPath = "/old/bin/kaya"
	plan, err := Build(old)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}
	plan, err = Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}
	hooks, err := os.ReadFile(ClaudeSettingsPath(opts))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(hooks), "/old/bin/kaya") || strings.Count(string(hooks), "hook session-start") != 1 {
		t.Fatalf("settings = %s", hooks)
	}
}

func TestInstall_leavesAVSCodeFileWithCommentsToTheUser(t *testing.T) {
	opts := testOptions(t)
	opts.LaunchPath = "/opt/bin/kaya-launch"
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
	if len(plan.Manual) != 1 || !strings.Contains(plan.Manual[0], WrapperSetting) {
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

func TestInstall_registersThePlanFeedbackHooks(t *testing.T) {
	opts := testOptions(t)
	opts.LaunchPath = "/opt/bin/kaya-launch"
	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(ClaudeSettingsPath(opts))
	if err != nil {
		t.Fatal(err)
	}
	var settings struct {
		Hooks map[string][]hookGroup `json:"hooks"`
	}
	if err := json.Unmarshal(data, &settings); err != nil {
		t.Fatal(err)
	}
	for event, want := range map[string]hookGroup{
		"UserPromptSubmit": {Hooks: []hookHandler{{Type: "command", Command: "/usr/local/bin/kaya hook user-prompt-submit", Timeout: 5}}},
		"PostToolUse":      {Matcher: "*", Hooks: []hookHandler{{Type: "command", Command: "/usr/local/bin/kaya hook post-tool-use", Timeout: 5}}},
	} {
		groups := settings.Hooks[event]
		if len(groups) != 1 || groups[0].Matcher != want.Matcher || len(groups[0].Hooks) != 1 || groups[0].Hooks[0] != want.Hooks[0] {
			t.Fatalf("%s = %+v", event, groups)
		}
	}
}

func TestInstall_removesAStaleConfigChangeHook(t *testing.T) {
	opts := testOptions(t)
	claude := ClaudeSettingsPath(opts)
	write(t, claude, `{"hooks":{"ConfigChange":[`+
		`{"hooks":[{"type":"command","command":"other.sh"}]},`+
		`{"hooks":[{"type":"command","command":"/old/bin/kaya hook config-change","timeout":5}]}]}}`)
	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(claude)
	if err != nil {
		t.Fatal(err)
	}
	var settings struct {
		Hooks map[string][]hookGroup `json:"hooks"`
	}
	if err := json.Unmarshal(data, &settings); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(data), "config-change") {
		t.Fatalf("the stale hook is still registered: %s", data)
	}
	if groups := settings.Hooks["ConfigChange"]; len(groups) != 1 || groups[0].Hooks[0].Command != "other.sh" {
		t.Fatalf("ConfigChange = %+v, want only other.sh", groups)
	}
	for _, event := range []string{"SessionStart", "SessionEnd", "PreToolUse", "UserPromptSubmit", "PostToolUse"} {
		if len(settings.Hooks[event]) != 1 {
			t.Fatalf("%s = %+v, want one kaya group", event, settings.Hooks[event])
		}
	}
}

func TestInstall_dropsTheConfigChangeEventWhenOnlyKayaUsedIt(t *testing.T) {
	opts := testOptions(t)
	claude := ClaudeSettingsPath(opts)
	write(t, claude, `{"hooks":{"ConfigChange":[{"hooks":[{"type":"command","command":"'/old/bin/kaya' hook config-change"}]}]}}`)
	plan, err := Build(opts)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Apply(plan.Changes, time.Now()); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(claude)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(data), "ConfigChange") || strings.Contains(string(data), "config-change") {
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
