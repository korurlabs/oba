package install

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

// DropInName is the file name of Kaya's drop-in among the managed settings of Claude Code, which
// merges managed-settings.json first and then every *.json of managed-settings.d in alphabetical
// order.
const DropInName = "50-kaya.json"

// InForceName is the file name of the in-force proof in Kaya's folder of the user config directory.
const InForceName = "in-force.json"

// recordCommand is the subcommand of oba kaya hook that captures a session for Kaya.
const recordCommand = "record"

// recordTimeout bounds the record hook in seconds: it only queues the event on disk.
const recordTimeout = 5

// hookEvents are the Claude Code events Kaya's delivery hooks into, with the subcommand and its
// timeout in seconds: session start may sync with Kaya (3 s online at most) and fetch plan
// feedback (2 s at most), the others read local state. PostToolUse and UserPromptSubmit hand the
// session the plan feedback oba kaya watch fetched.
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

// recordEvents are the Claude Code events the record hook captures for Kaya, with the tools whose
// calls it captures; an empty matcher captures every occurrence of the event.
var recordEvents = []struct{ Event, Matcher string }{
	{Event: "PreToolUse", Matcher: "Agent|Skill|ExitPlanMode"},
	{Event: "UserPromptSubmit"},
	{Event: "UserPromptExpansion"},
	{Event: "SessionStart"},
	{Event: "PostToolUse", Matcher: "Agent|Skill|Bash|Edit|Write|MultiEdit"},
	{Event: "PostToolUseFailure", Matcher: "Agent|Skill|Bash|Edit|Write|MultiEdit"},
	{Event: "SubagentStart"},
	{Event: "SubagentStop"},
	{Event: "Stop"},
	{Event: "SessionEnd"},
}

var sha256Hex = regexp.MustCompile(`^[0-9a-f]{64}$`)

// RefuseRoot fails for the effective user id 0. Every file install writes belongs to the
// developer, and the drop-in only needs root for one copy by a system binary, so oba never runs
// as root and a binary the developer can replace never gains root.
func RefuseRoot(euid int) error {
	if euid != 0 {
		return nil
	}
	return errors.New("oba kaya install does not run as root: run oba kaya install as your own user, without sudo; " +
		"oba kaya install --managed prints the one sudo line the managed drop-in needs")
}

// ManagedDir is the folder of the managed settings drop-ins of Claude Code on goos. Only root can
// write it.
func ManagedDir(goos string) (string, error) {
	switch goos {
	case "darwin":
		return "/Library/Application Support/ClaudeCode/managed-settings.d", nil
	case "linux":
		return "/etc/claude-code/managed-settings.d", nil
	}
	return "", fmt.Errorf("the managed settings of Claude Code are supported on macOS and Linux, not on %s", goos)
}

// rootGroup is the group with id 0 on goos, which owns the installed drop-in beside root.
func rootGroup(goos string) (string, error) {
	switch goos {
	case "darwin":
		return "wheel", nil
	case "linux":
		return "root", nil
	}
	return "", fmt.Errorf("the managed settings of Claude Code are supported on macOS and Linux, not on %s", goos)
}

// DropInPath is Kaya's drop-in in the managed settings folder managedDir.
func DropInPath(managedDir string) string {
	return filepath.Join(managedDir, DropInName)
}

// DefaultInForcePath is the in-force proof in the user config directory,
// <user config dir>/oba/kaya/in-force.json.
func DefaultInForcePath() (string, error) {
	base, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("find the user config directory: %w", err)
	}
	return filepath.Join(base, "oba", "kaya", InForceName), nil
}

type statusLine struct {
	Type    string `json:"type"`
	Command string `json:"command"`
}

// RenderManaged renders the drop-in that registers Kaya's delivery hooks, its record hook and its
// status line with the oba at obaPath, under the keys hooks and statusLine only. The same path
// always gives the same bytes, so an installed drop-in can be compared with the rendering of the
// running oba.
func RenderManaged(obaPath string) ([]byte, error) {
	if !filepath.IsAbs(obaPath) {
		return nil, fmt.Errorf("the oba path %q is not absolute", obaPath)
	}
	oba := shellQuote(obaPath)
	var events []string
	groups := map[string][]hookGroup{}
	add := func(event, matcher, command string, timeout int) {
		if _, ok := groups[event]; !ok {
			events = append(events, event)
		}
		groups[event] = append(groups[event], hookGroup{
			Matcher: matcher,
			Hooks:   []hookHandler{{Type: "command", Command: oba + " kaya hook " + command, Timeout: timeout}},
		})
	}
	for _, event := range hookEvents {
		add(event.Event, event.Matcher, event.Command, event.Timeout)
	}
	for _, event := range recordEvents {
		add(event.Event, event.Matcher, recordCommand, recordTimeout)
	}
	hooks := jsonedit.NewObject()
	for _, event := range events {
		if err := hooks.Set(event, groups[event]); err != nil {
			return nil, err
		}
	}
	dropIn := jsonedit.NewObject()
	if err := dropIn.Set("hooks", hooks); err != nil {
		return nil, err
	}
	if err := dropIn.Set("statusLine", statusLine{Type: "command", Command: oba + " kaya statusline"}); err != nil {
		return nil, err
	}
	return dropIn.Marshal()
}

// ManagedPlan is the drop-in install --managed renders and where it belongs.
type ManagedPlan struct {
	// Dir is the managed settings folder and Path the drop-in in it.
	Dir, Path string
	// Content is the rendered drop-in.
	Content []byte
	// Group owns the installed drop-in beside root: wheel on macOS, root on Linux.
	Group string
}

// BuildManaged renders the drop-in for the oba of opts and says where it belongs, writing nothing.
func BuildManaged(opts Options) (ManagedPlan, error) {
	if opts.ManagedDir == "" {
		return ManagedPlan{}, errors.New("the managed settings folder of Claude Code is not set")
	}
	group, err := rootGroup(opts.GOOS)
	if err != nil {
		return ManagedPlan{}, err
	}
	content, err := RenderManaged(opts.ObaPath)
	if err != nil {
		return ManagedPlan{}, err
	}
	return ManagedPlan{Dir: opts.ManagedDir, Path: DropInPath(opts.ManagedDir), Content: content, Group: group}, nil
}

// Stage writes the rendered drop-in to a new file in tempDir, readable by its owner only, and
// returns its path. It never opens the managed settings folder.
func (p ManagedPlan) Stage(tempDir string) (string, error) {
	file, err := os.CreateTemp(tempDir, "oba-kaya-drop-in-*.json")
	if err != nil {
		return "", fmt.Errorf("write the drop-in into %s: %w", tempDir, err)
	}
	name := file.Name()
	_, writeErr := file.Write(p.Content)
	if err := errors.Join(writeErr, file.Close()); err != nil {
		_ = os.Remove(name)
		return "", fmt.Errorf("write %s: %w", name, err)
	}
	return name, nil
}

// InstallStep is the one root step that installs the staged file as the drop-in. It runs only the
// system binaries /bin/mkdir and /usr/bin/install, which copies the file owned by root with mode
// 0644, so no code the developer can replace runs as root.
func (p ManagedPlan) InstallStep(staged string) string {
	return fmt.Sprintf("sudo /bin/mkdir -p %s && sudo /usr/bin/install -m 0644 -o root -g %s %s %s",
		shellQuote(p.Dir), p.Group, shellQuote(staged), shellQuote(p.Path))
}

// RemoveManagedStep is the root step that removes Kaya's drop-in from managedDir; it runs only
// the system binary /bin/rm.
func RemoveManagedStep(managedDir string) string {
	return "sudo /bin/rm -f " + shellQuote(DropInPath(managedDir))
}

// InForceProof proves that Claude Code applies the installed drop-in: oba kaya doctor --probe
// writes it when a session it started queued an event through the record hook, which only the
// drop-in registers. A higher managed source makes Claude Code skip the drop-in without a
// warning, so its mere presence proves nothing. The proof names the drop-in by the sha256 of its
// content, in lower-case hex, so a changed drop-in needs a new proof.
type InForceProof struct {
	DropInSha256 string    `json:"dropInSha256"`
	ProvedAt     time.Time `json:"provedAt"`
}

// LoadInForce reads the in-force proof at path. A missing file is no proof, and not an error.
func LoadInForce(path string) (InForceProof, bool, error) {
	data, err := readOptional(path)
	if err != nil || data == nil {
		return InForceProof{}, false, err
	}
	var proof InForceProof
	if err := json.Unmarshal(data, &proof); err != nil {
		return InForceProof{}, false, fmt.Errorf("%s: %w", path, err)
	}
	if !sha256Hex.MatchString(proof.DropInSha256) {
		return InForceProof{}, false, fmt.Errorf("%s: dropInSha256 %q is not a lower-case hex sha256", path, proof.DropInSha256)
	}
	if proof.ProvedAt.IsZero() {
		return InForceProof{}, false, fmt.Errorf("%s: provedAt is missing", path)
	}
	return proof, true, nil
}

// dropInState says whether Kaya's drop-in is installed and proved in force; reason says why it
// is not in force, for the user.
type dropInState struct {
	installed bool
	inForce   bool
	reason    string
}

// checkDropIn compares the in-force proof with the installed drop-in. Every failure to read
// either leaves the drop-in unproved and names the failure, so the user hooks are never removed
// on a guess.
func checkDropIn(opts Options) dropInState {
	path := DropInPath(opts.ManagedDir)
	installed, err := readOptional(path)
	if err != nil {
		return dropInState{installed: true, reason: "the drop-in is unreadable: " + err.Error()}
	}
	if installed == nil {
		return dropInState{reason: "no drop-in at " + path}
	}
	proof, ok, err := LoadInForce(opts.InForcePath)
	switch {
	case err != nil:
		return dropInState{installed: true, reason: "the in-force proof is not valid: " + err.Error()}
	case !ok:
		return dropInState{installed: true, reason: "no in-force proof at " + opts.InForcePath + " yet"}
	}
	sum := sha256.Sum256(installed)
	if proof.DropInSha256 != hex.EncodeToString(sum[:]) {
		return dropInState{installed: true, reason: "the in-force proof names another drop-in than the one installed"}
	}
	return dropInState{installed: true, inForce: true}
}
