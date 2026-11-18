// Package install sets a machine up for Kaya's config delivery: the VS Code extension starts
// Claude Code through kaya-launch, the terminal's claude runs through kaya launch, and the user
// settings of Claude Code register the kaya hooks. Every changed file is backed up first, and a
// second run changes nothing.
package install

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/korurlabs/kaya-cli/internal/jsonedit"
)

// WrapperSetting is the VS Code setting naming the executable the Claude Code extension starts
// Claude through; the extension passes its bundled binary as the first argument.
const WrapperSetting = "claudeCode.claudeProcessWrapper"

// LaunchName is the name under which the kaya binary acts as the VS Code wrapper.
const LaunchName = "kaya-launch"

const (
	blockStart = "# >>> kaya >>>"
	blockEnd   = "# <<< kaya <<<"
)

// Options says where the machine's files are.
type Options struct {
	Home string
	// ConfigHome is $XDG_CONFIG_HOME, empty for ~/.config.
	ConfigHome string
	// ClaudeConfigDir is $CLAUDE_CONFIG_DIR, empty for ~/.claude.
	ClaudeConfigDir string
	// GOOS picks the VS Code settings location.
	GOOS string
	// Shell is $SHELL, used when no rc file exists yet.
	Shell string
	// KayaPath is the absolute path of the kaya binary the hooks and the shell function run.
	KayaPath string
	// LaunchPath is an existing kaya-launch on PATH; empty creates the link LinkDir/kaya-launch.
	LaunchPath string
}

// Change is one file install writes. LinkTarget set means a symlink; otherwise After is the new
// content and Before the current one, nil when the file does not exist.
type Change struct {
	Path        string
	Description string
	Before      []byte
	After       []byte
	LinkTarget  string
}

// Plan is what install would do.
type Plan struct {
	Changes []Change
	// Unchanged names what is already in place.
	Unchanged []string
	// Manual lists what the user must do by hand, with the reason.
	Manual []string
}

// Build reads the machine's files and computes the changes, writing nothing.
func Build(opts Options) (Plan, error) {
	if !filepath.IsAbs(opts.KayaPath) {
		return Plan{}, fmt.Errorf("the kaya path %q is not absolute", opts.KayaPath)
	}
	var plan Plan
	launch := opts.LaunchPath
	if launch == "" {
		launch = filepath.Join(opts.Home, ".local", "share", "kaya", "bin", LaunchName)
		if err := plan.link(launch, opts.KayaPath); err != nil {
			return Plan{}, err
		}
	}
	if err := plan.vscode(opts, launch); err != nil {
		return Plan{}, err
	}
	if err := plan.shell(opts); err != nil {
		return Plan{}, err
	}
	if err := plan.hooks(opts); err != nil {
		return Plan{}, err
	}
	return plan, nil
}

func (p *Plan) link(path, target string) error {
	current, err := os.Readlink(path)
	switch {
	case err == nil && current == target:
		p.Unchanged = append(p.Unchanged, path+" links to "+target)
		return nil
	case err == nil:
	case errors.Is(err, fs.ErrNotExist):
	default:
		if info, statErr := os.Lstat(path); statErr == nil && info.Mode()&fs.ModeSymlink == 0 {
			return fmt.Errorf("%s exists and is not a link; remove it or put kaya-launch on PATH", path)
		}
		return fmt.Errorf("read %s: %w", path, err)
	}
	p.Changes = append(p.Changes, Change{Path: path, Description: "link " + LaunchName + " to " + target, LinkTarget: target})
	return nil
}

// VSCodeSettingsPath is the user settings file of VS Code.
func VSCodeSettingsPath(opts Options) string {
	if opts.GOOS == "darwin" {
		return filepath.Join(opts.Home, "Library", "Application Support", "Code", "User", "settings.json")
	}
	base := opts.ConfigHome
	if base == "" {
		base = filepath.Join(opts.Home, ".config")
	}
	return filepath.Join(base, "Code", "User", "settings.json")
}

func (p *Plan) vscode(opts Options, launch string) error {
	path := VSCodeSettingsPath(opts)
	before, err := readOptional(path)
	if err != nil {
		return err
	}
	manual := fmt.Sprintf("add %q: %q to %s", WrapperSetting, launch, path)
	if before == nil {
		if _, err := os.Stat(filepath.Dir(path)); err != nil {
			p.Manual = append(p.Manual, "VS Code was not found; when you install it, "+manual)
			return nil
		}
	}
	settings, err := jsonedit.Parse(before)
	if err != nil {
		// VS Code accepts comments and trailing commas; rewriting such a file would drop them.
		p.Manual = append(p.Manual, manual+" by hand (the file is not plain JSON: "+err.Error()+")")
		return nil
	}
	if raw, ok := settings.Get(WrapperSetting); ok {
		var current string
		if json.Unmarshal(raw, &current) == nil && current == launch {
			p.Unchanged = append(p.Unchanged, path+" starts Claude Code through "+launch)
			return nil
		}
	}
	if err := settings.Set(WrapperSetting, launch); err != nil {
		return err
	}
	after, err := settings.Marshal()
	if err != nil {
		return err
	}
	p.Changes = append(p.Changes, Change{Path: path, Description: "set " + WrapperSetting + " to " + launch, Before: before, After: after})
	return nil
}

func (p *Plan) shell(opts Options) error {
	block := strings.Join([]string{
		blockStart,
		"# Claude Code started from a terminal gets the checkout's signed Kaya config release first.",
		"claude() { " + shellQuote(opts.KayaPath) + ` launch claude "$@"; }`,
		blockEnd,
	}, "\n") + "\n"
	var files []string
	for _, name := range []string{".bashrc", ".zshrc"} {
		path := filepath.Join(opts.Home, name)
		if _, err := os.Lstat(path); err == nil {
			files = append(files, path)
		}
	}
	if len(files) == 0 {
		switch filepath.Base(opts.Shell) {
		case "bash":
			files = append(files, filepath.Join(opts.Home, ".bashrc"))
		case "zsh":
			files = append(files, filepath.Join(opts.Home, ".zshrc"))
		default:
			p.Manual = append(p.Manual, "no ~/.bashrc or ~/.zshrc; define claude as `"+shellQuote(opts.KayaPath)+" launch claude \"$@\"` in your shell")
			return nil
		}
	}
	for _, path := range files {
		before, err := readOptional(path)
		if err != nil {
			return err
		}
		after := replaceBlock(before, block)
		if bytes.Equal(before, after) {
			p.Unchanged = append(p.Unchanged, path+" defines claude through kaya launch")
			continue
		}
		p.Changes = append(p.Changes, Change{Path: path, Description: "define claude() through kaya launch", Before: before, After: after})
	}
	return nil
}

// replaceBlock puts block in place of the kaya block of content, or at its end.
func replaceBlock(content []byte, block string) []byte {
	text := string(content)
	start := strings.Index(text, blockStart)
	end := strings.Index(text, blockEnd)
	if start >= 0 && end > start {
		rest := text[end+len(blockEnd):]
		rest = strings.TrimPrefix(rest, "\n")
		return []byte(text[:start] + block + rest)
	}
	if text != "" && !strings.HasSuffix(text, "\n") {
		text += "\n"
	}
	if text != "" {
		text += "\n"
	}
	return []byte(text + block)
}

// hookEvents are the Claude Code events kaya hooks into, with the subcommand and its timeout in
// seconds: session start may sync with Kaya (3 s online at most) and fetch plan feedback (2 s at
// most), the others read local state. PostToolUse and UserPromptSubmit hand the session the plan
// feedback kaya watch fetched.
var hookEvents = []struct {
	Event, Command string
	Matcher        string
	Timeout        int
}{
	{Event: "SessionStart", Command: "session-start", Timeout: 10},
	{Event: "SessionEnd", Command: "session-end", Timeout: 5},
	{Event: "PreToolUse", Command: "pre-tool-use", Matcher: "*", Timeout: 60},
	{Event: "UserPromptSubmit", Command: "user-prompt-submit", Timeout: 5},
	{Event: "PostToolUse", Command: "post-tool-use", Matcher: "*", Timeout: 5},
}

// retiredHookEvents are events an older kaya registered and the current one no longer handles.
// Their kaya group is removed, so Claude Code does not call a subcommand that is gone.
var retiredHookEvents = []struct{ Event, Command string }{
	{Event: "ConfigChange", Command: "config-change"},
}

// ClaudeSettingsPath is the user settings file of Claude Code.
func ClaudeSettingsPath(opts Options) string {
	dir := opts.ClaudeConfigDir
	if dir == "" {
		dir = filepath.Join(opts.Home, ".claude")
	}
	return filepath.Join(dir, "settings.json")
}

type hookHandler struct {
	Type    string `json:"type"`
	Command string `json:"command"`
	Timeout int    `json:"timeout,omitempty"`
}

type hookGroup struct {
	Matcher string        `json:"matcher,omitempty"`
	Hooks   []hookHandler `json:"hooks"`
}

func (p *Plan) hooks(opts Options) error {
	path := ClaudeSettingsPath(opts)
	before, err := readOptional(path)
	if err != nil {
		return err
	}
	settings, err := jsonedit.Parse(before)
	if err != nil {
		return fmt.Errorf("%s: %w; fix the file and run kaya install again", path, err)
	}
	hooks, err := settings.Object("hooks")
	if err != nil {
		return fmt.Errorf("%s: %w", path, err)
	}
	for _, event := range retiredHookEvents {
		if _, ok := hooks.Get(event.Event); !ok {
			continue
		}
		kept, err := withoutKayaGroup(hooks, event.Event, event.Command)
		if err != nil {
			return fmt.Errorf("%s: %w", path, err)
		}
		if len(kept) == 0 {
			hooks.Delete(event.Event)
		} else if err := hooks.Set(event.Event, kept); err != nil {
			return err
		}
	}
	for _, event := range hookEvents {
		kept, err := withoutKayaGroup(hooks, event.Event, event.Command)
		if err != nil {
			return fmt.Errorf("%s: %w", path, err)
		}
		group, err := json.Marshal(hookGroup{
			Matcher: event.Matcher,
			Hooks:   []hookHandler{{Type: "command", Command: shellQuote(opts.KayaPath) + " hook " + event.Command, Timeout: event.Timeout}},
		})
		if err != nil {
			return fmt.Errorf("encode the %s hook: %w", event.Event, err)
		}
		kept = append(kept, group)
		if err := hooks.Set(event.Event, kept); err != nil {
			return err
		}
	}
	if err := settings.Set("hooks", hooks); err != nil {
		return err
	}
	after, err := settings.Marshal()
	if err != nil {
		return err
	}
	if before != nil && sameJSON(before, after) {
		p.Unchanged = append(p.Unchanged, path+" registers the kaya hooks")
		return nil
	}
	p.Changes = append(p.Changes, Change{Path: path, Description: "register the kaya hooks for SessionStart, SessionEnd, PreToolUse, UserPromptSubmit and PostToolUse", Before: before, After: after})
	return nil
}

// withoutKayaGroup returns the hook groups of event without the group whose single hook is
// `kaya hook <command>`, so that a run replaces kaya's own registration and keeps every other.
func withoutKayaGroup(hooks *jsonedit.Object, event, command string) ([]json.RawMessage, error) {
	var groups []json.RawMessage
	if raw, ok := hooks.Get(event); ok {
		if err := json.Unmarshal(raw, &groups); err != nil {
			return nil, fmt.Errorf("hooks.%s is not a list: %w", event, err)
		}
	}
	ours := regexp.MustCompile(`(^|/)kaya'? hook ` + regexp.QuoteMeta(command) + `$`)
	kept := make([]json.RawMessage, 0, len(groups)+1)
	for _, raw := range groups {
		var group hookGroup
		if json.Unmarshal(raw, &group) == nil && len(group.Hooks) == 1 && ours.MatchString(group.Hooks[0].Command) {
			continue
		}
		kept = append(kept, raw)
	}
	return kept, nil
}

// sameJSON compares two documents by value, so that a file indented differently from the
// CLI's output is not rewritten for nothing.
func sameJSON(a, b []byte) bool {
	var x, y any
	if json.Unmarshal(a, &x) != nil || json.Unmarshal(b, &y) != nil {
		return false
	}
	ax, errX := json.Marshal(x)
	by, errY := json.Marshal(y)
	return errX == nil && errY == nil && bytes.Equal(ax, by)
}

// Apply writes the changes: every existing file is first copied to <file>.kaya-backup-<time>,
// and each file is replaced by rename. A settings file that is a link is written through it.
func Apply(changes []Change, now time.Time) ([]string, error) {
	var backups []string
	for _, change := range changes {
		if change.LinkTarget != "" {
			if err := os.MkdirAll(filepath.Dir(change.Path), 0o750); err != nil {
				return backups, fmt.Errorf("create %s: %w", filepath.Dir(change.Path), err)
			}
			tmp := change.Path + ".kaya-tmp"
			_ = os.Remove(tmp)
			if err := os.Symlink(change.LinkTarget, tmp); err != nil {
				return backups, fmt.Errorf("link %s: %w", change.Path, err)
			}
			if err := os.Rename(tmp, change.Path); err != nil {
				_ = os.Remove(tmp)
				return backups, fmt.Errorf("link %s: %w", change.Path, err)
			}
			continue
		}
		path := change.Path
		if resolved, err := filepath.EvalSymlinks(path); err == nil {
			path = resolved
		}
		perm := fs.FileMode(0o644)
		if change.Before != nil {
			if info, err := os.Stat(path); err == nil {
				perm = info.Mode().Perm()
			}
			backup := fmt.Sprintf("%s.kaya-backup-%s", change.Path, now.Format("20060102-150405"))
			if err := os.WriteFile(backup, change.Before, 0o600); err != nil {
				return backups, fmt.Errorf("back up %s: %w", change.Path, err)
			}
			backups = append(backups, backup)
		}
		if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
			return backups, fmt.Errorf("create %s: %w", filepath.Dir(path), err)
		}
		if err := writeAtomic(path, change.After, perm); err != nil {
			return backups, err
		}
	}
	return backups, nil
}

func writeAtomic(path string, data []byte, perm fs.FileMode) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), "."+filepath.Base(path)+".tmp-*")
	if err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	name := tmp.Name()
	_, writeErr := tmp.Write(data)
	err = errors.Join(writeErr, tmp.Close())
	if err == nil {
		err = os.Chmod(name, perm)
	}
	if err == nil {
		err = os.Rename(name, path)
	}
	if err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}

func readOptional(path string) ([]byte, error) {
	data, err := os.ReadFile(path) // #nosec G304 -- a settings or rc file of the user's own home
	if errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}
	return data, nil
}

// shellQuote quotes a path for a POSIX shell when it needs it.
func shellQuote(s string) string {
	if regexp.MustCompile(`^[A-Za-z0-9_@%+=:,./-]+$`).MatchString(s) {
		return s
	}
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}
