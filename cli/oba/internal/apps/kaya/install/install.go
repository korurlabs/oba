// Package install sets a machine up for Kaya's config delivery: the VS Code extension starts
// Claude Code through oba-kaya-launch, the terminal's claude runs through oba kaya launch, and a
// managed settings drop-in of Claude Code registers Kaya's hooks and status line. The drop-in is
// rendered as the user and installed by root with a system binary. Kaya's hooks that an earlier
// install registered in the user settings are removed once the drop-in is proved in force. Every
// changed file is backed up first, and a second run changes nothing.
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

	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

// WrapperSetting is the VS Code setting naming the executable the Claude Code extension starts
// Claude through; the extension passes its bundled binary as the first argument.
const WrapperSetting = "claudeCode.claudeProcessWrapper"

// LaunchName is the name under which the oba binary acts as the VS Code wrapper.
const LaunchName = "oba-kaya-launch"

const (
	blockStart = "# >>> oba kaya >>>"
	blockEnd   = "# <<< oba kaya <<<"
	// legacyBlockStart and legacyBlockEnd mark the block the former kaya CLI wrote. A run
	// removes it, since the kaya binary it calls is gone.
	legacyBlockStart = "# >>> kaya >>>"
	legacyBlockEnd   = "# <<< kaya <<<"
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
	// ObaPath is the absolute path of the oba binary the hooks and the shell function run.
	ObaPath string
	// LaunchPath is an existing oba-kaya-launch on PATH; empty creates the link LinkDir/oba-kaya-launch.
	LaunchPath string
	// ManagedDir is the managed settings folder of Claude Code that holds Kaya's drop-in.
	ManagedDir string
	// InForcePath is the in-force proof of the drop-in.
	InForcePath string
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
	if !filepath.IsAbs(opts.ObaPath) {
		return Plan{}, fmt.Errorf("the oba path %q is not absolute", opts.ObaPath)
	}
	if !filepath.IsAbs(opts.ManagedDir) || !filepath.IsAbs(opts.InForcePath) {
		return Plan{}, fmt.Errorf("the managed settings folder %q and the in-force proof %q must be absolute paths", opts.ManagedDir, opts.InForcePath)
	}
	var plan Plan
	launch := opts.LaunchPath
	if launch == "" {
		launch = filepath.Join(opts.Home, ".local", "share", "oba", "bin", LaunchName)
		if err := plan.link(launch, opts.ObaPath); err != nil {
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
			return fmt.Errorf("%s exists and is not a link; remove it or put oba-kaya-launch on PATH", path)
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
		"claude() { " + shellQuote(opts.ObaPath) + ` kaya launch claude "$@"; }`,
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
			p.Manual = append(p.Manual, "no ~/.bashrc or ~/.zshrc; define claude as `"+shellQuote(opts.ObaPath)+" kaya launch claude \"$@\"` in your shell")
			return nil
		}
	}
	for _, path := range files {
		before, err := readOptional(path)
		if err != nil {
			return err
		}
		after := replaceBlock(removeBlock(before, legacyBlockStart, legacyBlockEnd), block)
		if bytes.Equal(before, after) {
			p.Unchanged = append(p.Unchanged, path+" defines claude through oba kaya launch")
			continue
		}
		p.Changes = append(p.Changes, Change{Path: path, Description: "define claude() through oba kaya launch", Before: before, After: after})
	}
	return nil
}

// removeBlock drops the block from start to end, with its line end, from content.
func removeBlock(content []byte, start, end string) []byte {
	text := string(content)
	i := strings.Index(text, start)
	j := strings.Index(text, end)
	if i < 0 || j < i {
		return content
	}
	rest := strings.TrimPrefix(text[j+len(end):], "\n")
	return []byte(text[:i] + rest)
}

// replaceBlock puts block in place of the oba kaya block of content, or at its end.
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

// hooks registers no hook: the managed drop-in does. It removes the hook of a retired event, and
// Kaya's hooks that an earlier install registered in the user settings once the drop-in is proved
// in force. Until then they stay, because a drop-in that Claude Code skips for a higher managed
// source would otherwise leave the machine without Kaya's hooks; removing them then keeps every
// hook from running twice.
func (p *Plan) hooks(opts Options) error {
	path := ClaudeSettingsPath(opts)
	before, err := readOptional(path)
	if err != nil {
		return err
	}
	settings, err := jsonedit.Parse(before)
	if err != nil {
		return fmt.Errorf("%s: %w; fix the file and run oba kaya install again", path, err)
	}
	hooks, err := settings.Object("hooks")
	if err != nil {
		return fmt.Errorf("%s: %w", path, err)
	}
	var done []string
	for _, event := range retiredHookEvents {
		removed, err := dropKayaGroups(hooks, []string{event.Event}, []string{event.Command})
		if err != nil {
			return fmt.Errorf("%s: %w", path, err)
		}
		if removed > 0 {
			done = append(done, "remove the "+event.Event+" hook that the current oba no longer handles")
		}
	}
	events, commands := kayaHooks()
	dropIn := checkDropIn(opts)
	if dropIn.inForce {
		removed, err := dropKayaGroups(hooks, events, commands)
		if err != nil {
			return fmt.Errorf("%s: %w", path, err)
		}
		if removed > 0 {
			done = append(done, "remove Kaya's hooks, which the managed drop-in registers")
		} else {
			p.Unchanged = append(p.Unchanged, path+" holds no Kaya hook; the managed drop-in registers them")
		}
	} else {
		kept, err := countKayaGroups(hooks, events, commands)
		if err != nil {
			return fmt.Errorf("%s: %w", path, err)
		}
		p.Manual = append(p.Manual, dropIn.advice(path, kept))
	}
	if len(done) == 0 {
		return nil
	}
	if hooks.Len() == 0 {
		settings.Delete("hooks")
	} else if err := settings.Set("hooks", hooks); err != nil {
		return err
	}
	after, err := settings.Marshal()
	if err != nil {
		return err
	}
	p.Changes = append(p.Changes, Change{Path: path, Description: strings.Join(done, "; "), Before: before, After: after})
	return nil
}

// advice tells the user how to register Kaya's hooks, or to prove the drop-in in force, and
// whether Kaya's hooks in the user settings at path stay until then.
func (s dropInState) advice(path string, kept int) string {
	if !s.installed {
		return "register Kaya's hooks and status line: run oba kaya install --managed and the sudo line it prints (" + s.reason + ")"
	}
	advice := "prove the managed drop-in in force: run oba kaya doctor --probe in a connected checkout (" + s.reason + ")"
	if kept > 0 {
		advice += ", then oba kaya install again; until then Kaya's hooks stay in " + path
	}
	return advice
}

// kayaHooks are the events and the subcommands of every hook the drop-in registers, which an
// earlier install may also have registered in the user settings.
func kayaHooks() (events, commands []string) {
	seen := map[string]bool{}
	for _, event := range hookEvents {
		if !seen[event.Event] {
			seen[event.Event] = true
			events = append(events, event.Event)
		}
		commands = append(commands, event.Command)
	}
	for _, event := range recordEvents {
		if !seen[event.Event] {
			seen[event.Event] = true
			events = append(events, event.Event)
		}
	}
	return events, append(commands, recordCommand)
}

// withoutKayaGroups returns the groups of event without Kaya's own groups of any of commands, and
// how many groups it left out.
func withoutKayaGroups(hooks *jsonedit.Object, event string, commands []string) ([]json.RawMessage, int, error) {
	raw, ok := hooks.Get(event)
	if !ok {
		return nil, 0, nil
	}
	var groups []json.RawMessage
	if err := json.Unmarshal(raw, &groups); err != nil {
		return nil, 0, fmt.Errorf("hooks.%s is not a list: %w", event, err)
	}
	kept := groups
	scratch := jsonedit.NewObject()
	for _, command := range commands {
		if err := scratch.Set(event, kept); err != nil {
			return nil, 0, err
		}
		var err error
		if kept, err = withoutKayaGroup(scratch, event, command); err != nil {
			return nil, 0, err
		}
	}
	return kept, len(groups) - len(kept), nil
}

// countKayaGroups is how many of Kaya's own groups of commands the events of hooks hold.
func countKayaGroups(hooks *jsonedit.Object, events, commands []string) (int, error) {
	count := 0
	for _, event := range events {
		_, ours, err := withoutKayaGroups(hooks, event, commands)
		if err != nil {
			return 0, err
		}
		count += ours
	}
	return count, nil
}

// dropKayaGroups removes Kaya's own groups of commands from the events of hooks, and an event
// left without groups, and returns how many groups it removed.
func dropKayaGroups(hooks *jsonedit.Object, events, commands []string) (int, error) {
	removed := 0
	for _, event := range events {
		kept, ours, err := withoutKayaGroups(hooks, event, commands)
		if err != nil {
			return 0, err
		}
		if ours == 0 {
			continue
		}
		removed += ours
		if len(kept) == 0 {
			hooks.Delete(event)
		} else if err := hooks.Set(event, kept); err != nil {
			return 0, err
		}
	}
	return removed, nil
}

// Apply writes the changes: every existing file is first copied to <file>.oba-backup-<time>,
// and each file is replaced by rename. A settings file that is a link is written through it.
func Apply(changes []Change, now time.Time) ([]string, error) {
	var backups []string
	for _, change := range changes {
		if change.LinkTarget != "" {
			if err := os.MkdirAll(filepath.Dir(change.Path), 0o750); err != nil {
				return backups, fmt.Errorf("create %s: %w", filepath.Dir(change.Path), err)
			}
			tmp := change.Path + ".oba-tmp"
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
			backup := fmt.Sprintf("%s.oba-backup-%s", change.Path, now.Format("20060102-150405"))
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
