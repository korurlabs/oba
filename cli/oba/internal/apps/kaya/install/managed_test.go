package install

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

type dropIn struct {
	Hooks      map[string][]hookGroup `json:"hooks"`
	StatusLine statusLine             `json:"statusLine"`
}

func renderDropIn(t *testing.T, obaPath string) (dropIn, []byte) {
	t.Helper()
	content, err := RenderManaged(obaPath)
	if err != nil {
		t.Fatal(err)
	}
	var parsed dropIn
	if err := json.Unmarshal(content, &parsed); err != nil {
		t.Fatalf("the drop-in is not JSON: %v\n%s", err, content)
	}
	return parsed, content
}

func TestRenderManaged_holdsOnlyTheHooksAndTheStatusLine(t *testing.T) {
	_, content := renderDropIn(t, "/opt/homebrew/bin/oba")
	object, err := jsonedit.Parse(content)
	if err != nil {
		t.Fatal(err)
	}
	_, hasHooks := object.Get("hooks")
	_, hasStatusLine := object.Get("statusLine")
	if object.Len() != 2 || !hasHooks || !hasStatusLine {
		t.Fatalf("drop-in keys are not hooks and statusLine only: %s", content)
	}
}

func TestRenderManaged_registersEveryKayaHookWithItsMatcherAndTimeout(t *testing.T) {
	const oba = "/opt/homebrew/bin/oba"
	parsed, _ := renderDropIn(t, oba)
	tests := []struct {
		event, matcher, command string
		timeout                 int
	}{
		{event: "SessionStart", command: "session-start", timeout: 10},
		{event: "SessionEnd", command: "session-end", timeout: 5},
		{event: "PreToolUse", matcher: "*", command: "pre-tool-use", timeout: 60},
		{event: "UserPromptSubmit", command: "user-prompt-submit", timeout: 5},
		{event: "PostToolUse", matcher: "*", command: "post-tool-use", timeout: 5},
		{event: "PreToolUse", matcher: "Agent|Skill|ExitPlanMode", command: "record", timeout: 5},
		{event: "UserPromptSubmit", command: "record", timeout: 5},
		{event: "UserPromptExpansion", command: "record", timeout: 5},
		{event: "SessionStart", command: "record", timeout: 5},
		{event: "PostToolUse", matcher: "Agent|Skill|Bash|Edit|Write|MultiEdit", command: "record", timeout: 5},
		{event: "PostToolUseFailure", matcher: "Agent|Skill|Bash|Edit|Write|MultiEdit", command: "record", timeout: 5},
		{event: "SubagentStart", command: "record", timeout: 5},
		{event: "SubagentStop", command: "record", timeout: 5},
		{event: "Stop", command: "record", timeout: 5},
		{event: "SessionEnd", command: "record", timeout: 5},
	}
	groups := 0
	for _, list := range parsed.Hooks {
		groups += len(list)
	}
	if groups != len(tests) {
		t.Fatalf("the drop-in holds %d hook groups, want %d: %+v", groups, len(tests), parsed.Hooks)
	}
	for _, tt := range tests {
		t.Run(tt.event+" "+tt.command, func(t *testing.T) {
			want := hookHandler{Type: "command", Command: oba + " kaya hook " + tt.command, Timeout: tt.timeout}
			for _, group := range parsed.Hooks[tt.event] {
				if len(group.Hooks) == 1 && group.Hooks[0] == want && group.Matcher == tt.matcher {
					return
				}
			}
			t.Fatalf("%s holds no group %q with matcher %q: %+v", tt.event, want.Command, tt.matcher, parsed.Hooks[tt.event])
		})
	}
	if parsed.StatusLine != (statusLine{Type: "command", Command: oba + " kaya statusline"}) {
		t.Fatalf("statusLine = %+v", parsed.StatusLine)
	}
}

func TestRenderManaged_isStableAndQuotesTheObaPath(t *testing.T) {
	first, err := RenderManaged("/Users/dev/my tools/oba")
	if err != nil {
		t.Fatal(err)
	}
	second, err := RenderManaged("/Users/dev/my tools/oba")
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(first, second) {
		t.Fatal("two renderings of the same oba path differ")
	}
	parsed, _ := renderDropIn(t, "/Users/dev/my tools/oba")
	if got := parsed.StatusLine.Command; got != "'/Users/dev/my tools/oba' kaya statusline" {
		t.Fatalf("statusLine command = %q", got)
	}
	if _, err := RenderManaged("bin/oba"); err == nil {
		t.Fatal("rendered a drop-in with a relative oba path")
	}
}

func TestBuildManaged_stagesIntoTheTempFolderAndNeverTouchesTheManagedFolder(t *testing.T) {
	opts := testOptions(t)
	tempDir := t.TempDir()
	plan, err := BuildManaged(opts)
	if err != nil {
		t.Fatal(err)
	}
	if plan.Path != filepath.Join(opts.ManagedDir, "50-kaya.json") {
		t.Fatalf("path = %s", plan.Path)
	}

	staged, err := plan.Stage(tempDir)
	if err != nil {
		t.Fatal(err)
	}

	if filepath.Dir(staged) != tempDir {
		t.Fatalf("staged %s outside the temp folder %s", staged, tempDir)
	}
	if got := readFile(t, staged); got != string(plan.Content) {
		t.Fatalf("staged content = %s", got)
	}
	info, err := os.Stat(staged)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("staged mode = %v, want 0600", info.Mode().Perm())
	}
	if _, err := os.Stat(opts.ManagedDir); !os.IsNotExist(err) {
		t.Fatalf("the managed folder was touched: %v", err)
	}
}

func TestBuildManaged_refusesAnUnsupportedSystemOrARelativeObaPath(t *testing.T) {
	for _, change := range []func(*Options){
		func(o *Options) { o.GOOS = "windows" },
		func(o *Options) { o.ObaPath = "oba" },
		func(o *Options) { o.ManagedDir = "" },
	} {
		opts := testOptions(t)
		change(&opts)
		if _, err := BuildManaged(opts); err == nil {
			t.Fatalf("built a drop-in for %+v", opts)
		}
	}
}

func TestManagedPlan_installStepRunsOnlyMkdirAndInstallAsRoot(t *testing.T) {
	tests := []struct{ goos, group string }{
		{goos: "darwin", group: "wheel"},
		{goos: "linux", group: "root"},
	}
	for _, tt := range tests {
		t.Run(tt.goos, func(t *testing.T) {
			dir, err := ManagedDir(tt.goos)
			if err != nil {
				t.Fatal(err)
			}
			opts := testOptions(t)
			opts.GOOS, opts.ManagedDir = tt.goos, dir
			plan, err := BuildManaged(opts)
			if err != nil {
				t.Fatal(err)
			}

			step := plan.InstallStep("/tmp/oba-kaya-drop-in-1.json")

			parts := strings.Split(step, " && ")
			if len(parts) != 2 {
				t.Fatalf("step = %s, want two commands", step)
			}
			if want := "sudo /bin/mkdir -p " + shellQuote(dir); parts[0] != want {
				t.Fatalf("first command = %s, want %s", parts[0], want)
			}
			want := "sudo /usr/bin/install -m 0644 -o root -g " + tt.group + " /tmp/oba-kaya-drop-in-1.json " + shellQuote(filepath.Join(dir, "50-kaya.json"))
			if parts[1] != want {
				t.Fatalf("second command = %s, want %s", parts[1], want)
			}
			if strings.Contains(step, "oba kaya") || strings.Count(step, "sudo ") != 2 {
				t.Fatalf("step = %s runs more than the two system binaries", step)
			}
		})
	}
}

func TestRemoveManagedStep_runsOnlyRm(t *testing.T) {
	dir, err := ManagedDir("darwin")
	if err != nil {
		t.Fatal(err)
	}
	if got, want := RemoveManagedStep(dir), "sudo /bin/rm -f '/Library/Application Support/ClaudeCode/managed-settings.d/50-kaya.json'"; got != want {
		t.Fatalf("remove step = %s, want %s", got, want)
	}
}

func TestManagedDir_namesTheFolderOfEachSystem(t *testing.T) {
	tests := []struct {
		goos, want string
		wantErr    bool
	}{
		{goos: "darwin", want: "/Library/Application Support/ClaudeCode/managed-settings.d"},
		{goos: "linux", want: "/etc/claude-code/managed-settings.d"},
		{goos: "windows", wantErr: true},
	}
	for _, tt := range tests {
		got, err := ManagedDir(tt.goos)
		if (err != nil) != tt.wantErr || got != tt.want {
			t.Fatalf("ManagedDir(%s) = %q, %v", tt.goos, got, err)
		}
	}
}

func TestRefuseRoot_refusesOnlyTheRootUser(t *testing.T) {
	err := RefuseRoot(0)
	if err == nil || !strings.Contains(err.Error(), "run oba kaya install as your own user, without sudo") {
		t.Fatalf("RefuseRoot(0) = %v", err)
	}
	if err := RefuseRoot(501); err != nil {
		t.Fatalf("RefuseRoot(501) = %v", err)
	}
}

func TestLoadInForce_readsTheProofAndRefusesAMalformedOne(t *testing.T) {
	sha := strings.Repeat("0f", 32)
	tests := []struct {
		name    string
		content string
		wantOK  bool
		wantErr bool
	}{
		{name: "no proof"},
		{name: "a valid proof", content: `{"dropInSha256":"` + sha + `","provedAt":"2026-11-23T10:00:00Z"}`, wantOK: true},
		{name: "not JSON", content: `{"dropInSha256":`, wantErr: true},
		{name: "upper-case hex", content: `{"dropInSha256":"` + strings.ToUpper(sha) + `","provedAt":"2026-11-23T10:00:00Z"}`, wantErr: true},
		{name: "no provedAt", content: `{"dropInSha256":"` + sha + `"}`, wantErr: true},
		{name: "a provedAt that is not RFC 3339", content: `{"dropInSha256":"` + sha + `","provedAt":"yesterday"}`, wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), InForceName)
			if tt.content != "" {
				write(t, path, tt.content)
			}
			proof, ok, err := LoadInForce(path)
			if ok != tt.wantOK || (err != nil) != tt.wantErr {
				t.Fatalf("LoadInForce = %+v, %v, %v", proof, ok, err)
			}
			if ok && (proof.DropInSha256 != sha || proof.ProvedAt.IsZero()) {
				t.Fatalf("proof = %+v", proof)
			}
		})
	}
}
