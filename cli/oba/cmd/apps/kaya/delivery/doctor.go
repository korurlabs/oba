package delivery

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
	"strings"
	"syscall"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/install"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// Statuses of a doctor check. Only a failed check makes doctor exit 1.
const (
	checkOK      = "ok"
	checkFailed  = "fail"
	checkSkipped = "skipped"
)

// Scopes of a doctor check, in the order doctor prints them.
const (
	scopeMachine  = "machine"
	scopeCheckout = "checkout"
	scopeProbe    = "probe"
)

const (
	// doctorAPITimeout bounds each group of Kaya calls doctor makes, the machine token lookup
	// included.
	doctorAPITimeout = 30 * time.Second
	// versionTimeout bounds `oba --version` of the oba the drop-in names.
	versionTimeout = 10 * time.Second
	// probeTimeout bounds each claude -p session of the probe.
	probeTimeout = 3 * time.Minute
	// recordWait is how long the probe looks for the session's record event after claude exits,
	// for a hook still writing it.
	recordWait = 2 * time.Second
	// probePrompt is the probe session's one prompt: the session only has to start and take a
	// turn for its hooks to run, so the cheapest answer will do.
	probePrompt = "Reply with the single word OK."
	// agentsMDLoaded is what the debug log of Claude Code's built-in agents-md plugin says before the
	// paths of the AGENTS.md files it loaded, joined by agentsMDSeparator.
	agentsMDLoaded    = "AGENTS.md loaded: "
	agentsMDSeparator = ", "
)

// cutoverPaths are what a repository stops tracking when Kaya delivers to it, besides every path
// its releases deliver: the agent config folders, the root AGENTS.md, the file that named the
// repository to Kaya before recognition by remotes, and the GitHub Actions file of the earlier sync.
var cutoverPaths = []string{".agents", ".claude", ".kaya", "AGENTS.md", delivery.ConfigFile, ".github/workflows/agents-sync.yml"}

// kayaRoots are the top-level folders that belong to Kaya entirely once a repository is cut over.
var kayaRoots = []string{".agents", ".claude", ".kaya"}

// The parts of the machine that a test replaces: the managed settings folder, which only root
// can write, the file information of the drop-in and its folders, the user's sign-in that lists
// the signing keys, and the version of the running oba.
var (
	doctorManagedDir  = func() (string, error) { return install.ManagedDir(runtime.GOOS) }
	doctorLstat       = os.Lstat
	doctorSigningKeys = func(ctx context.Context) ([]client.SigningKey, error) {
		api, err := client.ForOrganization()
		if err != nil {
			return nil, err
		}
		return api.SigningKeys(ctx)
	}
	doctorVersion = func(cmd *cobra.Command) string { return cmd.Root().Version }
)

// doctorCheck is one finding of oba kaya doctor.
type doctorCheck struct {
	Scope  string `json:"scope"`
	Name   string `json:"name"`
	Status string `json:"status"`
	Detail string `json:"detail"`
}

// doctorCheckout is the checkout doctor ran in.
type doctorCheckout struct {
	Root       string `json:"root"`
	Kind       string `json:"kind"`
	Remote     string `json:"remote,omitempty"`
	RemoteName string `json:"remoteName,omitempty"`
}

// doctorCutover is what the cutover pull request of a checkout not cut over yet deletes.
type doctorCutover struct {
	Paths   []string `json:"paths"`
	Command string   `json:"command,omitempty"`
	// Incomplete says why the paths only Kaya's current release delivers could not be listed.
	Incomplete string `json:"incomplete,omitempty"`
}

// doctorReport is what doctor prints, and with --json its result.
type doctorReport struct {
	Passed   bool            `json:"passed"`
	Checkout *doctorCheckout `json:"checkout,omitempty"`
	Checks   []doctorCheck   `json:"checks"`
	Cutover  *doctorCutover  `json:"cutover,omitempty"`
}

func (r *doctorReport) add(scope, name, status, detail string) {
	r.Checks = append(r.Checks, doctorCheck{Scope: scope, Name: name, Status: status, Detail: detail})
}

// doctor holds what the checks of one run share.
type doctor struct {
	ctx     context.Context
	opts    install.Options
	version string
	report  doctorReport
	// dropIn is the installed drop-in as read at the start of the run; nil when none is installed.
	dropIn []byte
	// inForce: the in-force proof names the installed drop-in.
	inForce  bool
	machine  delivery.Machine
	listErr  error
	checkout *delivery.Checkout
	active   *delivery.Release
}

func newDoctorCmd() *cobra.Command {
	var probe bool
	cmd := &cobra.Command{
		Use:   "doctor",
		Short: "Check that this machine, and the checkout it runs in, run Claude Code with Kaya",
		Long: "Machine checks: the managed settings drop-in " + install.DropInName + " exists, parses and names an " +
			"existing oba of the running version; it is owned by root with mode 0644, its two folders are owned by " +
			"root and writable by root only, and its content is what this oba renders; it is proved in force; the " +
			"global git config runs Kaya's pre-commit and post-checkout hooks through this oba; the Claude Code user " +
			"settings hold no Kaya hook once the drop-in is proved in force; claude() runs Claude Code through oba kaya " +
			"launch; the machine token is found and accepted; the repository list is refreshed and not empty; and, " +
			"signed in with oba login, Kaya signs releases with a key this oba trusts (skipped without a sign-in).\n\n" +
			"Checkout checks, when run in a checkout Kaya delivers to: the remote that names it; the active release " +
			"verifies and is Kaya's current one; every delivered path is in place; the exclude block covers them; git " +
			"tracks none of them; git runs Kaya's hooks there; and Claude Code loads instruction files from its folder. " +
			"In a checkout not cut over yet, which still syncs its agent config from .agents/.managed.json, doctor lists " +
			"every tracked path the cutover pull request deletes and prints the git rm line; that list does not fail " +
			"doctor, so the exit code there is the one of the machine checks.\n\n" +
			"--probe, in a checkout Kaya recognises, runs one claude -p session with the project and local settings " +
			"only, and checks that it loads every delivered skill and subagent, and that its record hook queued an " +
			"event. Only the managed drop-in registers that hook, so the event proves the drop-in in force: doctor " +
			"then writes the in-force proof, after which oba kaya install removes Kaya's earlier hooks from the user " +
			"settings. Without the event doctor deletes the proof and names the higher managed source that makes " +
			"Claude Code skip the drop-in. A second claude -p session, with the user settings as a normal session " +
			"has, checks that Claude Code loads the root AGENTS.md, which it does not in a session without the user " +
			"settings; its record event never counts as proof. Each session runs a model turn on your account.\n\n" +
			"doctor exits 0 when no check fails and 1 when one does; --json prints the same as a result.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			d, err := newDoctor(cmd)
			if err != nil {
				return err
			}
			d.run(probe)
			if jsonout.Enabled() {
				encoded, err := json.Marshal(d.report)
				if err != nil {
					return fmt.Errorf("failed to encode the JSON output: %w", err)
				}
				if err := jsonresult.SetPartial(encoded); err != nil {
					return err
				}
			} else {
				d.report.print(cmd.OutOrStdout())
			}
			if !d.report.Passed {
				return &exitcode.Error{Code: 1}
			}
			return nil
		},
	}
	cmd.Flags().BoolVar(&probe, "probe", false, "run claude -p sessions to prove the drop-in in force and the delivery loaded")
	return cmd
}

func newDoctor(cmd *cobra.Command) (*doctor, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return nil, fmt.Errorf("find the home directory: %w", err)
	}
	oba, err := doctorObaPath()
	if err != nil {
		return nil, err
	}
	managedDir, err := doctorManagedDir()
	if err != nil {
		return nil, err
	}
	inForce, err := install.DefaultInForcePath()
	if err != nil {
		return nil, err
	}
	return &doctor{
		ctx:     cmd.Context(),
		version: doctorVersion(cmd),
		opts: install.Options{
			Home: home, ConfigHome: os.Getenv("XDG_CONFIG_HOME"), ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR"),
			GOOS: runtime.GOOS, Shell: os.Getenv("SHELL"), ObaPath: oba, ManagedDir: managedDir, InForcePath: inForce,
		},
	}, nil
}

// doctorObaPath is the oba that oba kaya install writes into the drop-in, the git hook entries and
// claude(): oba as found on PATH, which survives an upgrade by Homebrew, else this binary.
func doctorObaPath() (string, error) {
	if path, err := exec.LookPath("oba"); err == nil {
		if abs, err := filepath.Abs(path); err == nil {
			return abs, nil
		}
	}
	self, err := os.Executable()
	if err != nil {
		return "", fmt.Errorf("find the oba binary: %w", err)
	}
	return self, nil
}

// run runs every check. The in-force proof and the user settings are checked after the probe,
// which writes or deletes the proof.
func (d *doctor) run(probe bool) {
	d.checkDropIn()
	d.checkDropInOwner()
	d.checkDropInContent()
	d.checkGitHookEntries()
	d.checkClaudeFunction()
	d.connect()
	d.checkSigningKeys()
	d.checkCheckout()
	if probe {
		d.probe()
	}
	d.checkInForce()
	d.checkUserSettings()
	d.report.Passed = !slices.ContainsFunc(d.report.Checks, func(c doctorCheck) bool { return c.Status == checkFailed })
}

func (d *doctor) dropInPath() string {
	return install.DropInPath(d.opts.ManagedDir)
}

// rerunManaged is the fix of a drop-in that is missing or differs from this oba's rendering.
const rerunManaged = "run oba kaya install --managed and the sudo line it prints"

func (d *doctor) checkDropIn() {
	const name = "drop-in"
	path := d.dropInPath()
	content, err := os.ReadFile(path) // #nosec G304 -- Kaya's drop-in in the managed settings folder
	switch {
	case errors.Is(err, fs.ErrNotExist):
		d.report.add(scopeMachine, name, checkFailed, "no drop-in at "+path+", so no session runs Kaya's hooks, session capture or status line: "+rerunManaged)
		return
	case err != nil:
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("read %s: %v", path, err))
		return
	}
	d.dropIn = content
	var parsed struct {
		StatusLine struct {
			Command string `json:"command"`
		} `json:"statusLine"`
	}
	if err := json.Unmarshal(content, &parsed); err != nil {
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("%s is not valid JSON (%v): %s", path, err, rerunManaged))
		return
	}
	named, ok := obaOfCommand(parsed.StatusLine.Command, " kaya statusline")
	if !ok {
		d.report.add(scopeMachine, name, checkFailed, path+" names no oba in its status line: "+rerunManaged)
		return
	}
	if info, err := os.Stat(named); err != nil || !info.Mode().IsRegular() || info.Mode().Perm()&0o111 == 0 {
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("%s runs %s, which is missing or not executable, so every hook of it fails: %s", path, named, rerunManaged))
		return
	}
	version, err := obaVersion(d.ctx, named)
	if err != nil {
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("%s runs %s, whose version could not be read: %v", path, named, err))
		return
	}
	if version != d.version {
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("%s runs %s, which is version %s, while this oba is version %s: %s with the oba you use",
			path, named, version, d.version, rerunManaged))
		return
	}
	d.report.add(scopeMachine, name, checkOK, fmt.Sprintf("%s runs Kaya's hooks and status line with %s, version %s", path, named, version))
}

// obaOfCommand is the oba path a drop-in command runs, the command being the path, quoted for a
// shell when it needs it, followed by suffix.
func obaOfCommand(command, suffix string) (string, bool) {
	quoted, ok := strings.CutSuffix(command, suffix)
	if !ok || quoted == "" {
		return "", false
	}
	if len(quoted) >= 2 && strings.HasPrefix(quoted, "'") && strings.HasSuffix(quoted, "'") {
		quoted = strings.ReplaceAll(quoted[1:len(quoted)-1], `'\''`, `'`)
	}
	return quoted, filepath.IsAbs(quoted)
}

// obaVersion is the version the oba at path reports.
func obaVersion(ctx context.Context, path string) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, versionTimeout)
	defer cancel()
	out, err := exec.CommandContext(ctx, path, "--version").Output() // #nosec G204 -- the oba the installed drop-in runs in every session
	if err != nil {
		return "", fmt.Errorf("%s --version: %w", path, err)
	}
	text := strings.TrimSpace(string(out))
	_, version, ok := strings.Cut(text, "version ")
	if !ok {
		return "", fmt.Errorf("%s --version printed %q, which names no version", path, text)
	}
	return version, nil
}

func (d *doctor) checkDropInOwner() {
	const name = "drop-in owner"
	if d.dropIn == nil {
		d.report.add(scopeMachine, name, checkSkipped, "no drop-in is installed")
		return
	}
	path := d.dropInPath()
	var problems []string
	info, err := doctorLstat(path)
	switch {
	case err != nil:
		problems = append(problems, err.Error())
	case !info.Mode().IsRegular():
		problems = append(problems, path+" is not a regular file")
	default:
		problems = append(problems, ownerProblems(path, info)...)
		if perm := info.Mode().Perm(); perm != 0o644 {
			problems = append(problems, fmt.Sprintf("%s has mode %04o, not 0644", path, perm))
		}
	}
	for _, dir := range []string{d.opts.ManagedDir, filepath.Dir(d.opts.ManagedDir)} {
		info, err := doctorLstat(dir)
		switch {
		case err != nil:
			problems = append(problems, err.Error())
		case !info.IsDir():
			problems = append(problems, dir+" is not a directory")
		default:
			problems = append(problems, ownerProblems(dir, info)...)
			if perm := info.Mode().Perm(); perm&0o022 != 0 {
				problems = append(problems, fmt.Sprintf("%s is writable by group or others (mode %04o)", dir, perm))
			}
		}
	}
	if len(problems) > 0 {
		d.report.add(scopeMachine, name, checkFailed, strings.Join(problems, "; ")+
			"; whoever can write them can change the hooks every Claude Code session runs: an administrator runs the "+
			"sudo line of oba kaya install --managed again and keeps both folders owned by root and writable by root only")
		return
	}
	d.report.add(scopeMachine, name, checkOK, path+" is owned by root with mode 0644, and its two folders are owned by root and writable by root only")
}

// ownerProblems names a file not owned by root.
func ownerProblems(path string, info fs.FileInfo) []string {
	stat, ok := info.Sys().(*syscall.Stat_t)
	switch {
	case !ok:
		return []string{"the owner of " + path + " could not be read"}
	case stat.Uid != 0:
		return []string{fmt.Sprintf("%s is owned by user id %d, not root", path, stat.Uid)}
	}
	return nil
}

func (d *doctor) checkDropInContent() {
	const name = "drop-in content"
	if d.dropIn == nil {
		d.report.add(scopeMachine, name, checkSkipped, "no drop-in is installed")
		return
	}
	want, err := install.RenderManaged(d.opts.ObaPath)
	if err != nil {
		d.report.add(scopeMachine, name, checkFailed, err.Error())
		return
	}
	if !bytes.Equal(d.dropIn, want) {
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("%s is not the drop-in this oba renders for %s: %s", d.dropInPath(), d.opts.ObaPath, rerunManaged))
		return
	}
	d.report.add(scopeMachine, name, checkOK, d.dropInPath()+" is the drop-in this oba renders for "+d.opts.ObaPath)
}

func (d *doctor) checkGitHookEntries() {
	const name = "git hook entries"
	// oba kaya install computes what it would set in the same way, so doctor and install agree on
	// what a correct entry is.
	plan, err := install.BuildGitHooks(d.opts)
	if err != nil {
		d.report.add(scopeMachine, name, checkFailed, err.Error())
		return
	}
	var problems []string
	for _, setting := range plan.Settings {
		problems = append(problems, "the global git config does not set "+setting.Key+" to the one value oba kaya install writes for "+d.opts.ObaPath)
	}
	if len(problems) > 0 {
		problems[len(problems)-1] += ": run oba kaya install"
	}
	problems = append(problems, plan.Manual...)
	if len(problems) > 0 {
		d.report.add(scopeMachine, name, checkFailed, strings.Join(problems, "; "))
		return
	}
	d.report.add(scopeMachine, name, checkOK, "the global git config runs "+d.opts.ObaPath+" kaya git-hook for pre-commit and post-checkout")
}

// gitError adds what git printed on stderr to its error.
func gitError(err error) error {
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) && len(bytes.TrimSpace(exitErr.Stderr)) > 0 {
		return fmt.Errorf("%w: %s", err, strings.TrimSpace(string(exitErr.Stderr)))
	}
	return err
}

func (d *doctor) checkClaudeFunction() {
	const name = "claude function"
	missing, err := install.MissingShellFunction(d.opts)
	switch {
	case err != nil:
		d.report.add(scopeMachine, name, checkFailed, err.Error())
	case len(missing) > 0:
		d.report.add(scopeMachine, name, checkFailed, "claude() does not run Claude Code through "+d.opts.ObaPath+" kaya launch: "+
			strings.Join(missing, "; ")+"; run oba kaya install")
	default:
		d.report.add(scopeMachine, name, checkOK, "claude() runs Claude Code through oba kaya launch")
	}
}

// connect finds the machine token and refreshes the repository list with it, which both proves
// that Kaya accepts the token and lets the checkout be recognised by an up-to-date list.
func (d *doctor) connect() {
	ctx, cancel := context.WithTimeout(d.ctx, doctorAPITimeout)
	defer cancel()
	m, err := delivery.ConnectMachine(ctx)
	if err != nil {
		d.listErr = err
		d.report.add(scopeMachine, "machine token", checkFailed, err.Error())
		d.report.add(scopeMachine, "repository list", checkFailed, "not refreshed: "+err.Error())
		return
	}
	d.machine = m
	list, err := m.RefreshRepositoryList(ctx)
	d.listErr = err
	var apiErr *client.APIError
	switch {
	case m.API == nil:
		d.report.add(scopeMachine, "machine token", checkFailed, "no machine token: "+errorText(m.Err))
	case errors.As(err, &apiErr) && apiErr.Status == http.StatusUnauthorized:
		d.report.add(scopeMachine, "machine token", checkFailed, "Kaya refused the machine token: create one with New machine in Kaya "+
			"and set it in "+machine.EnvToken+" or your Kagi personal environment")
	case err != nil:
		d.report.add(scopeMachine, "machine token", checkSkipped, "found, but Kaya was not reached to accept it")
	default:
		d.report.add(scopeMachine, "machine token", checkOK, "found and accepted by Kaya")
	}
	switch {
	case err != nil:
		fetched := list.FetchedAt
		if fetched == "" {
			fetched = "never"
		}
		d.report.add(scopeMachine, "repository list", checkFailed, fmt.Sprintf("%v; the cached list, which the hooks recognise checkouts by, "+
			"holds %d repositories and was fetched %s", err, len(list.Remotes), fetched))
	case len(list.Remotes) == 0:
		d.report.add(scopeMachine, "repository list", checkFailed, "Kaya lists no repository of organization "+list.OrganizationID+
			", so no checkout is delivered until an OWNER adds its repository in Kaya")
	default:
		d.report.add(scopeMachine, "repository list", checkOK, fmt.Sprintf("%d repositories of organization %s, refreshed now", len(list.Remotes), list.OrganizationID))
	}
}

func errorText(err error) string {
	if err == nil {
		return "unknown reason"
	}
	return err.Error()
}

func (d *doctor) checkSigningKeys() {
	const name = "signing key"
	ctx, cancel := context.WithTimeout(d.ctx, doctorAPITimeout)
	defer cancel()
	keys, err := doctorSigningKeys(ctx)
	if errors.Is(err, auth.ErrNotLoggedIn) {
		d.report.add(scopeMachine, name, checkSkipped, "not signed in: run oba login and doctor again to check that Kaya signs releases with a key this oba trusts")
		return
	}
	if err != nil {
		d.report.add(scopeMachine, name, checkFailed, "GET /kaya/signing-keys: "+err.Error())
		return
	}
	trusted, err := delivery.TrustedKeys(machine.APIURL())
	if err != nil {
		d.report.add(scopeMachine, name, checkFailed, err.Error())
		return
	}
	ids := make([]string, 0, len(keys))
	for _, key := range keys {
		raw, err := base64.StdEncoding.DecodeString(key.PublicKey)
		if err == nil && len(trusted[key.KeyID]) > 0 && bytes.Equal(raw, trusted[key.KeyID]) {
			d.report.add(scopeMachine, name, checkOK, "Kaya signs releases with key "+key.KeyID+", which this oba trusts")
			return
		}
		ids = append(ids, key.KeyID)
	}
	if len(ids) == 0 {
		ids = append(ids, "none")
	}
	d.report.add(scopeMachine, name, checkFailed, "no signing key of Kaya ("+strings.Join(ids, ", ")+") is one this oba trusts with the same "+
		"public key, so it refuses every release: install an oba release that trusts Kaya's key")
}

func (d *doctor) checkCheckout() {
	const name = "recognition"
	checkout, err := delivery.CurrentCheckout()
	if checkout == nil {
		d.report.add(scopeCheckout, name, checkFailed, errorText(err))
		return
	}
	d.checkout = checkout
	d.report.Checkout = &doctorCheckout{Root: checkout.Root, Kind: checkout.Kind.String(), Remote: checkout.Remote, RemoteName: checkout.RemoteName}
	if checkout.Kind == delivery.NotRepository {
		d.report.add(scopeCheckout, name, checkSkipped, checkout.Root+" is in no git checkout, so only the machine checks ran")
		return
	}
	if err != nil {
		d.report.add(scopeCheckout, name, checkFailed, err.Error())
		return
	}
	detail := delivery.Recognition(checkout)
	for _, warning := range delivery.UnresolvedWarnings(checkout) {
		detail += " Warning: " + warning
	}
	if checkout.Kind == delivery.NotKaya {
		// Most checkouts on a laptop are not Kaya's; only the machine checks apply to them.
		d.report.add(scopeCheckout, name, checkSkipped, detail)
		return
	}
	d.report.add(scopeCheckout, name, checkOK, detail)
	switch checkout.Kind {
	case delivery.NotCutOver:
		d.listCutover()
	case delivery.Delivered:
		d.checkDelivered()
	}
}

func (d *doctor) checkDelivered() {
	c := d.checkout
	deliverer, err := delivery.OpenWith(c, d.machine)
	if err != nil {
		d.report.add(scopeCheckout, "release", checkFailed, err.Error())
		return
	}
	active, _, err := deliverer.ActiveRelease()
	switch {
	case err != nil:
		d.report.add(scopeCheckout, "release", checkFailed, fmt.Sprintf("the active release does not verify (%v): run oba kaya sync", err))
	case active == nil:
		d.report.add(scopeCheckout, "release", checkFailed, "no verified release is active in this checkout: run oba kaya sync")
	default:
		d.active = active
		d.report.add(scopeCheckout, "release", checkOK, fmt.Sprintf("release %d is active and verifies with the keys compiled into oba", active.Manifest.Number))
		d.checkCurrent()
		d.checkPlaced(deliverer)
		d.checkExcludes()
	}
	var manifest delivery.Manifest
	if d.active != nil {
		manifest = d.active.Manifest
	}
	d.checkTracked(manifest)
	d.checkGitHooksRun()
	d.checkInstructionFolder()
}

func (d *doctor) checkCurrent() {
	const name = "current release"
	if d.machine.API == nil || d.listErr != nil {
		d.report.add(scopeCheckout, name, checkSkipped, "Kaya was not reached, so the active release was not compared with Kaya's current one")
		return
	}
	request, normalized, err := d.checkout.Remotes()
	if err != nil {
		d.report.add(scopeCheckout, name, checkFailed, err.Error())
		return
	}
	ctx, cancel := context.WithTimeout(d.ctx, doctorAPITimeout)
	defer cancel()
	current, _, _, err := d.machine.API.CurrentRelease(ctx, request, "")
	active := d.active.Manifest.Number
	switch {
	case err != nil:
		d.report.add(scopeCheckout, name, checkFailed, "ask Kaya for the current release: "+err.Error())
	case current.Number == 0:
		d.report.add(scopeCheckout, name, checkFailed, "Kaya has no release of "+normalized+" yet, but release "+fmt.Sprint(active)+" is active here")
	case current.Number != active || !strings.EqualFold(current.ManifestSHA256, d.active.Signed.ManifestSHA256):
		d.report.add(scopeCheckout, name, checkFailed, fmt.Sprintf("release %d is active, Kaya's current one is %d: run oba kaya sync, "+
			"and restart the Claude Code sessions of this checkout, each of which keeps the release it started on", active, current.Number))
	default:
		d.report.add(scopeCheckout, name, checkOK, fmt.Sprintf("release %d is Kaya's current one", active))
	}
}

func (d *doctor) checkPlaced(deliverer *delivery.Deliverer) {
	const name = "placed"
	files := len(d.active.Manifest.Files)
	problems := d.checkout.Inspect(d.active.Manifest, deliverer.Cache)
	if len(problems) > 0 {
		names := make([]string, 0, len(problems))
		for _, problem := range problems {
			names = append(names, problem.String())
		}
		d.report.add(scopeCheckout, name, checkFailed, fmt.Sprintf("%d of the %d delivered paths are not as release %d delivers them (%s): run oba kaya sync, which restores them",
			len(problems), files, d.active.Manifest.Number, strings.Join(names, "; ")))
		return
	}
	d.report.add(scopeCheckout, name, checkOK, fmt.Sprintf("all %d delivered paths are in place", files))
}

func (d *doctor) checkExcludes() {
	const name = "exclude block"
	entries, err := delivery.ExcludeEntries(d.active.Manifest)
	if err != nil {
		d.report.add(scopeCheckout, name, checkFailed, err.Error())
		return
	}
	out, err := gitOutput(d.ctx, d.checkout.Root, "rev-parse", "--git-common-dir")
	if err != nil {
		d.report.add(scopeCheckout, name, checkFailed, err.Error())
		return
	}
	commonDir := strings.TrimRight(out, "\r\n")
	if !filepath.IsAbs(commonDir) {
		commonDir = filepath.Join(d.checkout.Root, commonDir)
	}
	path := filepath.Join(commonDir, "info", "exclude")
	content, err := os.ReadFile(path) // #nosec G304 -- info/exclude of the checkout's own git directory
	if err != nil && !errors.Is(err, fs.ErrNotExist) {
		d.report.add(scopeCheckout, name, checkFailed, fmt.Sprintf("read %s: %v", path, err))
		return
	}
	block := excludeBlock(content)
	var missing []string
	for _, entry := range entries {
		if !block[entry] {
			missing = append(missing, entry)
		}
	}
	if len(missing) > 0 {
		d.report.add(scopeCheckout, name, checkFailed, fmt.Sprintf("Kaya's block in %s lacks %s, so git status shows those paths: run oba kaya sync, which adds them",
			path, strings.Join(missing, ", ")))
		return
	}
	d.report.add(scopeCheckout, name, checkOK, "Kaya's block in "+path+" keeps every delivered path out of git")
}

// excludeBlock is the set of patterns between Kaya's marker lines of an info/exclude file.
func excludeBlock(content []byte) map[string]bool {
	patterns := map[string]bool{}
	inBlock := false
	for _, line := range strings.Split(string(content), "\n") {
		text := strings.TrimRight(line, "\r")
		switch {
		case text == delivery.ExcludeBegin:
			inBlock = true
		case text == delivery.ExcludeEnd:
			inBlock = false
		case inBlock && text != "" && !strings.HasPrefix(text, "#"):
			patterns[text] = true
		}
	}
	return patterns
}

func (d *doctor) checkTracked(manifest delivery.Manifest) {
	const name = "tracked paths"
	paths, err := trackedAgentConfig(d.ctx, d.checkout.Root, manifest)
	switch {
	case err != nil:
		d.report.add(scopeCheckout, name, checkFailed, err.Error())
	case len(paths) > 0:
		d.report.add(scopeCheckout, name, checkFailed, fmt.Sprintf("git tracks %d path(s) Kaya delivers or owns (%s), which Kaya's links and files replace: "+
			"untrack them, keeping the files, with %s", len(paths), strings.Join(paths, ", "), cutoverCommand(paths, true)))
	default:
		d.report.add(scopeCheckout, name, checkOK, "git tracks no delivered path and nothing below .agents/, .claude/ or .kaya/")
	}
}

// trackedAgentConfig lists what git tracks in the checkout at root of the cutover paths and the
// paths manifest delivers, sorted.
func trackedAgentConfig(ctx context.Context, root string, manifest delivery.Manifest) ([]string, error) {
	args := append([]string{"--literal-pathspecs", "ls-files", "-z", "--"}, cutoverPaths...)
	for _, file := range manifest.Files {
		args = append(args, file.Path)
	}
	out, err := gitOutput(ctx, root, args...)
	if err != nil {
		return nil, err
	}
	paths := []string{}
	for _, path := range strings.Split(out, "\x00") {
		if path != "" {
			paths = append(paths, path)
		}
	}
	slices.Sort(paths)
	return slices.Compact(paths), nil
}

// cutoverCommand is the git rm line that takes paths out of git: each folder Kaya owns as a whole,
// every other path on its own. cached keeps the files on disk.
func cutoverCommand(paths []string, cached bool) string {
	var targets []string
	seen := map[string]bool{}
	for _, path := range paths {
		target := path
		if top, _, found := strings.Cut(path, "/"); found && slices.Contains(kayaRoots, top) {
			target = top
		}
		if !seen[target] {
			seen[target] = true
			targets = append(targets, quoteArg(target))
		}
	}
	command := "git rm -r"
	if cached {
		command += " --cached"
	}
	return command + " -- " + strings.Join(targets, " ")
}

var plainArg = regexp.MustCompile(`^[A-Za-z0-9_@%+=:,./-]+$`)

// quoteArg quotes an argument for a POSIX shell when it needs it.
func quoteArg(s string) string {
	if plainArg.MatchString(s) {
		return s
	}
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}

func (d *doctor) checkGitHooksRun() {
	const name = "git hooks"
	var problems []string
	for _, event := range install.GitHookEvents {
		hook := install.GitHookName(event)
		out, err := exec.CommandContext(d.ctx, "git", "-C", d.checkout.Root, "hook", "list", event).Output() // #nosec G204 -- fixed git subcommand with one of Kaya's hook events
		var exitErr *exec.ExitError
		switch {
		case err == nil && slices.Contains(strings.Split(strings.TrimSpace(string(out)), "\n"), hook):
		case err == nil || (errors.As(err, &exitErr) && bytes.Contains(exitErr.Stderr, []byte("no hooks found"))):
			problems = append(problems, "git runs no "+hook+" hook for "+event+" here: run oba kaya install")
		default:
			problems = append(problems, fmt.Sprintf("git hook list %s failed (%v): Kaya's git hooks need git 2.54 or newer, which runs hooks from the git config", event, gitError(err)))
		}
	}
	if len(problems) > 0 {
		d.report.add(scopeCheckout, name, checkFailed, strings.Join(problems, "; "))
		return
	}
	d.report.add(scopeCheckout, name, checkOK, "git runs kaya-pre-commit and kaya-post-checkout here")
}

func (d *doctor) checkInstructionFolder() {
	const name = "instruction files"
	if folder := skippedInstructionFolder(d.checkout.Root); folder != "" {
		d.report.add(scopeCheckout, name, checkFailed, d.checkout.Root+" is below "+folder+", a folder laid out like a session's scratchpad, "+
			"from which Claude Code loads no AGENTS.md, CLAUDE.md or rule file: use a checkout outside it")
		return
	}
	d.report.add(scopeCheckout, name, checkOK, "Claude Code loads instruction files from "+d.checkout.Root)
}

var sessionIDPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// skippedInstructionFolder is the folder of path from which Claude Code loads no instruction file:
// a scratchpad folder below a session id, as Claude Code lays out a session's scratchpad, or a
// folder named .artifact-work. Empty when there is none.
func skippedInstructionFolder(path string) string {
	segments := strings.Split(filepath.ToSlash(path), "/")
	for i := 1; i < len(segments); i++ {
		if (segments[i] == "scratchpad" && sessionIDPattern.MatchString(segments[i-1])) || segments[i] == ".artifact-work" {
			return filepath.FromSlash(strings.Join(segments[:i+1], "/"))
		}
	}
	return ""
}

// listCutover lists what the cutover pull request of a checkout not cut over yet deletes.
func (d *doctor) listCutover() {
	manifest, readErr := d.currentManifest()
	paths, err := trackedAgentConfig(d.ctx, d.checkout.Root, manifest)
	if err != nil {
		d.report.add(scopeCheckout, "cutover list", checkFailed, err.Error())
		return
	}
	cutover := &doctorCutover{Paths: paths}
	if readErr != nil {
		cutover.Incomplete = "a tracked path that only Kaya's current release delivers is not listed, since that release was not read: " + readErr.Error()
	}
	if len(paths) > 0 {
		cutover.Command = cutoverCommand(paths, false)
	}
	d.report.Cutover = cutover
}

// currentManifest is the verified manifest of Kaya's current release of the checkout's repository;
// empty while the repository has none.
func (d *doctor) currentManifest() (delivery.Manifest, error) {
	if d.machine.API == nil || d.listErr != nil {
		return delivery.Manifest{}, errors.New("the Kaya API was not reached")
	}
	request, normalized, err := d.checkout.Remotes()
	if err != nil {
		return delivery.Manifest{}, err
	}
	ctx, cancel := context.WithTimeout(d.ctx, doctorAPITimeout)
	defer cancel()
	current, _, _, err := d.machine.API.CurrentRelease(ctx, request, "")
	if err != nil {
		return delivery.Manifest{}, err
	}
	if current.ReleaseID == "" {
		return delivery.Manifest{}, nil
	}
	release, err := d.machine.API.ReleaseByID(ctx, current.ReleaseID)
	if err != nil {
		return delivery.Manifest{}, err
	}
	keys, err := delivery.TrustedKeys(machine.APIURL())
	if err != nil {
		return delivery.Manifest{}, err
	}
	manifest, err := delivery.VerifyRelease(release, keys)
	if err != nil {
		return delivery.Manifest{}, err
	}
	return manifest, delivery.CheckOrigin(manifest, d.checkout.Organization, normalized, 0)
}

// gitOutput runs git in root and returns what it printed on stdout.
func gitOutput(ctx context.Context, root string, args ...string) (string, error) {
	out, err := exec.CommandContext(ctx, "git", append([]string{"-C", root}, args...)...).Output() // #nosec G204 -- fixed git subcommands on the checkout
	if err != nil {
		return "", fmt.Errorf("git %s in %s: %w", args[min(len(args)-1, 1)], root, gitError(err))
	}
	return string(out), nil
}

// probeRun is what the probe session showed.
type probeRun struct {
	sessionID string
	skills    []string
	agents    []string
	recorded  bool
	recordErr error
}

// claudeSession is what one claude -p session of the probe showed: its init event, with the
// session id filled in, and its debug log.
type claudeSession struct {
	init     probeInit
	debugLog []byte
	debugErr error
}

// probeInit is the init event claude -p prints with --output-format stream-json.
type probeInit struct {
	Type      string   `json:"type"`
	Subtype   string   `json:"subtype"`
	SessionID string   `json:"session_id"`
	Skills    []string `json:"skills"`
	Agents    []string `json:"agents"`
}

// probe runs the probe session in the checkout. Its record event alone writes the in-force proof,
// since only the drop-in registers oba kaya hook record: user settings are left out of the session,
// and a project file that registers the hook itself stops the probe. A probe that proves nothing
// either way, outside a recognised checkout or when the session did not start, leaves the proof as
// it was. The AGENTS.md check runs a second session of its own, which never touches the proof.
func (d *doctor) probe() {
	const name = "probe"
	const untouched = "; the in-force proof was left as it was"
	c := d.checkout
	if c == nil || (c.Kind != delivery.Delivered && c.Kind != delivery.NotCutOver) {
		d.report.add(scopeProbe, name, checkFailed, "run oba kaya doctor --probe in a checkout Kaya recognises: anywhere else the record hook queues nothing"+untouched)
		return
	}
	registrar, err := projectRecordHook(c.Root)
	if err != nil {
		d.report.add(scopeProbe, name, checkFailed, err.Error()+untouched)
		return
	}
	if registrar != "" {
		d.report.add(scopeProbe, name, checkFailed, registrar+" registers oba kaya hook record itself, so a record event would not show that Claude Code applies the drop-in: "+
			"remove that hook from it"+untouched)
		return
	}
	claude, err := exec.LookPath("claude")
	if err != nil {
		d.report.add(scopeProbe, name, checkFailed, fmt.Sprintf("claude was not found on PATH (%v)%s", err, untouched))
		return
	}
	dropIn := d.dropIn
	run, err := runProbe(d.ctx, claude, c.Root)
	if err != nil {
		d.report.add(scopeProbe, name, checkFailed, err.Error()+untouched)
		return
	}
	d.report.add(scopeProbe, name, checkOK, "claude -p ran session "+run.sessionID+" in "+c.Root)
	d.checkProbeLoaded(run)
	d.checkProbeAgentsMD(claude)
	d.checkProbeRecord(run, dropIn)
}

// projectRecordHook names the project settings file of the checkout that registers Kaya's record
// hook; empty when none does.
func projectRecordHook(root string) (string, error) {
	for _, name := range []string{"settings.json", "settings.local.json"} {
		path := filepath.Join(root, ".claude", name)
		data, err := os.ReadFile(path) // #nosec G304 -- a project settings file of the checkout
		if errors.Is(err, fs.ErrNotExist) {
			continue
		}
		if err != nil {
			return "", fmt.Errorf("read %s: %w", path, err)
		}
		if bytes.Contains(data, []byte("kaya hook record")) {
			return path, nil
		}
	}
	return "", nil
}

// runProbe runs the probe session in root and returns what it showed. The session loads the
// project and local settings, never the user's, besides the managed ones, which Claude Code always
// applies: an earlier oba kaya install, or the former kaya CLI, may have registered the record hook
// in the user settings, and its event would then claim a drop-in in force that Claude Code skips.
func runProbe(ctx context.Context, claude, root string) (probeRun, error) {
	session, err := runSession(ctx, claude, root, "--setting-sources", "project,local")
	if err != nil {
		return probeRun{}, err
	}
	sessionID := session.init.SessionID
	if !delivery.ValidSessionID(sessionID) {
		return probeRun{}, fmt.Errorf("claude -p named the session %q, which is not a session id", sessionID)
	}
	run := probeRun{sessionID: sessionID, skills: session.init.Skills, agents: session.init.Agents}
	run.recorded, run.recordErr = recordedEvent(sessionID)
	return run, nil
}

// runAgentsMDSession runs a second claude -p session in root with every settings source, as a
// normal session has, and returns its debug log. Claude Code's built-in agents-md plugin loads no
// AGENTS.md in a session that leaves the user settings out, so the probe session cannot show
// whether a normal session loads the checkout's AGENTS.md. A record hook in the user settings may
// queue this session's events, so nothing here looks for them: they would prove nothing about the
// drop-in.
func runAgentsMDSession(ctx context.Context, claude, root string) ([]byte, error) {
	session, err := runSession(ctx, claude, root)
	if err != nil {
		return nil, err
	}
	if session.debugErr != nil {
		return nil, fmt.Errorf("the session wrote no debug log: %w", session.debugErr)
	}
	return session.debugLog, nil
}

// runSession runs one claude -p session of the probe in root, with a new session id, its own debug
// log in a temp folder, and settings besides the arguments every session of the probe gets. It
// fails when claude prints no init event, that is when the session did not start.
func runSession(ctx context.Context, claude, root string, settings ...string) (claudeSession, error) {
	sessionID, err := newSessionID()
	if err != nil {
		return claudeSession{}, err
	}
	dir, err := os.MkdirTemp("", "oba-kaya-probe-*")
	if err != nil {
		return claudeSession{}, fmt.Errorf("create the folder of the probe's debug log: %w", err)
	}
	defer func() {
		// rule-10-no-op -- reason: a debug log left in the temp folder harms nothing and the system clears it.
		_ = os.RemoveAll(dir)
	}()
	debugFile := filepath.Join(dir, "debug.log")
	ctx, cancel := context.WithTimeout(ctx, probeTimeout)
	defer cancel()
	args := append([]string{"-p", probePrompt, "--output-format", "stream-json", "--verbose"}, settings...)
	args = append(args, "--session-id", sessionID, "--no-session-persistence", "--debug-file", debugFile)
	cmd := exec.CommandContext(ctx, claude, args...) // #nosec G204 -- claude from PATH with fixed arguments
	cmd.Dir = root
	var stdout, stderr bytes.Buffer
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	runErr := cmd.Run()
	init, found := findInit(stdout.Bytes())
	if !found {
		return claudeSession{}, fmt.Errorf("claude -p started no session (%v): %s", errorText(runErr), lastLine(stderr.String()))
	}
	if init.SessionID == "" {
		init.SessionID = sessionID
	}
	session := claudeSession{init: init}
	session.debugLog, session.debugErr = os.ReadFile(debugFile) // #nosec G304 -- the debug log of the session, in its own temp folder
	return session, nil
}

// findInit finds the init event among the lines claude -p printed; other lines are passed over.
func findInit(stdout []byte) (probeInit, bool) {
	for _, line := range bytes.Split(stdout, []byte("\n")) {
		var event probeInit
		if json.Unmarshal(line, &event) == nil && event.Type == "system" && event.Subtype == "init" {
			return event, true
		}
	}
	return probeInit{}, false
}

// lastLine is the last non-empty line of text, for a short error.
func lastLine(text string) string {
	lines := strings.Split(strings.TrimSpace(text), "\n")
	if last := strings.TrimSpace(lines[len(lines)-1]); last != "" {
		return last
	}
	return "it printed nothing on stderr"
}

// newSessionID is a random version 4 UUID, the form Claude Code takes as a session id.
func newSessionID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("make a session id: %w", err)
	}
	b[6] = b[6]&0x0f | 0x40
	b[8] = b[8]&0x3f | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}

// recordedEvent reports whether the record hook queued an event of the session, waiting briefly
// for a hook that is still writing it.
func recordedEvent(sessionID string) (bool, error) {
	stateDir, err := delivery.RecorderStateDir()
	if err != nil {
		return false, err
	}
	path := filepath.Join(capture.SessionDir(stateDir, sessionID), capture.EventsFile)
	deadline := time.Now().Add(recordWait)
	for {
		info, err := os.Stat(path)
		switch {
		case err == nil && info.Size() > 0:
			return true, nil
		case err != nil && !errors.Is(err, fs.ErrNotExist):
			return false, fmt.Errorf("inspect %s: %w", path, err)
		case time.Now().After(deadline):
			return false, nil
		}
		time.Sleep(100 * time.Millisecond)
	}
}

// deliveredSkillsAndAgents are the files that define the skills and subagents a release delivers
// where Claude Code finds them, relative to the checkout: .claude/skills/<name>/SKILL.md and
// .claude/agents/<name>.md.
func deliveredSkillsAndAgents(release *delivery.Release) (skills, agents []string) {
	if release == nil {
		return nil, nil
	}
	for _, file := range release.Manifest.Files {
		parts := strings.Split(file.Path, "/")
		switch {
		case len(parts) >= 3 && parts[0] == ".claude" && parts[1] == "skills":
			skills = append(skills, strings.Join(parts[:3], "/")+"/SKILL.md")
		case len(parts) == 3 && parts[0] == ".claude" && parts[1] == "agents" && strings.HasSuffix(parts[2], ".md"):
			agents = append(agents, file.Path)
		}
	}
	slices.Sort(skills)
	slices.Sort(agents)
	return slices.Compact(skills), slices.Compact(agents)
}

// loadedName finds the skill or subagent defined by the file at rel in loaded, the names the
// session's init event lists. Claude Code names it by the name of its front matter, else by its
// folder (a skill) or file (a subagent); either is accepted. It returns the name to report.
func loadedName(root, rel string, loaded []string) (string, bool) {
	base := filepath.Base(rel)
	if base == "SKILL.md" {
		base = filepath.Base(filepath.Dir(rel))
	}
	base = strings.TrimSuffix(base, ".md")
	if slices.Contains(loaded, base) {
		return base, true
	}
	if named := frontMatterName(filepath.Join(root, filepath.FromSlash(rel))); named != "" && slices.Contains(loaded, named) {
		return named, true
	}
	return base, false
}

// frontMatterName is the name field of the YAML front matter of the file at path; empty when the
// file has none or cannot be read, in which case the folder or file name is what counts.
func frontMatterName(path string) string {
	data, err := os.ReadFile(path) // #nosec G304 -- a delivered skill or subagent of the checkout
	if err != nil {
		return ""
	}
	lines := strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n")
	if len(lines) == 0 || strings.TrimSpace(lines[0]) != "---" {
		return ""
	}
	for _, line := range lines[1:] {
		if strings.TrimSpace(line) == "---" {
			return ""
		}
		if value, found := strings.CutPrefix(line, "name:"); found {
			return strings.Trim(strings.TrimSpace(value), `"'`)
		}
	}
	return ""
}

func (d *doctor) checkProbeLoaded(run probeRun) {
	const name = "skills and subagents"
	if d.active == nil {
		reason := "no verified release is active here, so there is no delivered skill or subagent to look for"
		if d.checkout.Kind == delivery.NotCutOver {
			reason = "Kaya delivers nothing to a checkout not cut over yet"
		}
		d.report.add(scopeProbe, name, checkSkipped, reason)
		return
	}
	skills, agents := deliveredSkillsAndAgents(d.active)
	var missing []string
	for _, skill := range skills {
		if named, ok := loadedName(d.checkout.Root, skill, run.skills); !ok {
			missing = append(missing, "skill "+named)
		}
	}
	for _, agent := range agents {
		if named, ok := loadedName(d.checkout.Root, agent, run.agents); !ok {
			missing = append(missing, "subagent "+named)
		}
	}
	if len(missing) > 0 {
		d.report.add(scopeProbe, name, checkFailed, "the session did not load the delivered "+strings.Join(missing, ", ")+": run oba kaya sync and doctor again")
		return
	}
	d.report.add(scopeProbe, name, checkOK, fmt.Sprintf("the session loaded all %d delivered skills and %d delivered subagents", len(skills), len(agents)))
}

// checkProbeAgentsMD checks in a second claude -p session, with the user settings, that Claude
// Code loads the checkout's root AGENTS.md; see runAgentsMDSession.
func (d *doctor) checkProbeAgentsMD(claude string) {
	const name = "AGENTS.md"
	const session = "a claude -p session with every settings source, as a normal session has"
	want := filepath.Join(d.checkout.Root, "AGENTS.md")
	if !exists(want) {
		d.report.add(scopeProbe, name, checkFailed, "the checkout has no root AGENTS.md for Claude Code to load")
		return
	}
	log, err := runAgentsMDSession(d.ctx, claude, d.checkout.Root)
	switch {
	case err != nil:
		d.report.add(scopeProbe, name, checkFailed, session+", failed: "+err.Error())
	case !loadedAgentsMD(log, want):
		d.report.add(scopeProbe, name, checkFailed, "the debug log of "+session+", does not name "+want+" as loaded. Claude Code loads "+
			"AGENTS.md through its built-in plugin cc-plugin-agents-md@builtin, which must be on, and by default only where no CLAUDE.md, "+
			".claude/CLAUDE.md or CLAUDE.local.md lies in the checkout or a folder above it; the instructionFiles option of that plugin "+
			"under pluginConfigs in the user or managed settings leaves AGENTS.md out too when it is claude-md or managed-only")
	default:
		d.report.add(scopeProbe, name, checkOK, session+", loaded "+want)
	}
}

func exists(path string) bool {
	_, err := os.Lstat(path)
	return err == nil
}

// loadedAgentsMD reports whether the debug log names want among the AGENTS.md files the agents-md
// plugin loaded. The plugin names every AGENTS.md it loads in the checkout and the folders above it
// on one line, so the line is split into its paths; the whole rest of the line is a candidate too,
// for a single path that itself holds the separator.
func loadedAgentsMD(log []byte, want string) bool {
	wantResolved := resolvedPath(want)
	for _, line := range strings.Split(string(log), "\n") {
		_, paths, found := strings.Cut(line, agentsMDLoaded)
		if !found {
			continue
		}
		paths = strings.TrimSpace(paths)
		for _, path := range append([]string{paths}, strings.Split(paths, agentsMDSeparator)...) {
			if resolvedPath(strings.TrimSpace(path)) == wantResolved {
				return true
			}
		}
	}
	return false
}

// resolvedPath is path with its links resolved, so that /tmp and /private/tmp compare equal; the
// cleaned path when it does not resolve.
func resolvedPath(path string) string {
	if resolved, err := filepath.EvalSymlinks(path); err == nil {
		return resolved
	}
	return filepath.Clean(path)
}

// skippedDropIn explains a drop-in that is installed but did not run its record hook.
const skippedDropIn = "Under the default managedSourcesBehavior \"first-wins\", a higher managed source (server-managed settings, " +
	"or an MDM profile) makes Claude Code skip the drop-in without a warning; /status then names it on its \"Skipped sources\" " +
	"line, and an administrator sets managedSourcesBehavior \"merge\" in the highest managed source deployed (Claude Code " +
	"2.1.242 or later) to apply the drop-in too. A record hook that fails has the same effect: claude --debug names its error"

func (d *doctor) checkProbeRecord(run probeRun, dropIn []byte) {
	const name = "record event"
	path := d.dropInPath()
	if run.recordErr != nil {
		d.report.add(scopeProbe, name, checkFailed, run.recordErr.Error()+"; the in-force proof was left as it was")
		return
	}
	if run.recorded && dropIn != nil {
		proof := install.InForceProof{DropInSha256: install.DropInSha256(dropIn), ProvedAt: time.Now()}
		if err := install.SaveInForce(d.opts.InForcePath, proof); err != nil {
			d.report.add(scopeProbe, name, checkFailed, "the drop-in is in force, but its proof was not written: "+err.Error())
			return
		}
		d.report.add(scopeProbe, name, checkOK, "a record event of the session reached the queue, so Claude Code applies "+path+
			"; the in-force proof "+d.opts.InForcePath+" names it, and oba kaya install now removes Kaya's earlier hooks from the user settings")
		return
	}
	var detail string
	switch {
	case run.recorded:
		detail = "a record event reached the queue, but no drop-in is installed at " + path + ", so another managed settings source registers the record hook: " + rerunManaged
	case dropIn == nil:
		detail = "no record event of the session reached the queue: no drop-in is installed at " + path + ": " + rerunManaged
	default:
		detail = "no record event of the session reached the queue, so Claude Code did not apply " + path + ". " + skippedDropIn
	}
	detail += ". The in-force proof was deleted, so oba kaya install keeps Kaya's hooks in the user settings"
	if err := install.RemoveInForce(d.opts.InForcePath); err != nil {
		detail += ", but deleting it failed: " + err.Error()
	}
	d.report.add(scopeProbe, name, checkFailed, detail)
}

func (d *doctor) checkInForce() {
	const name = "drop-in in force"
	if d.dropIn == nil {
		d.report.add(scopeMachine, name, checkSkipped, "no drop-in is installed")
		return
	}
	proof, ok, err := install.LoadInForce(d.opts.InForcePath)
	const prove = "run oba kaya doctor --probe in a checkout Kaya recognises"
	switch {
	case err != nil:
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("the in-force proof is not valid (%v): %s", err, prove))
	case !ok:
		d.report.add(scopeMachine, name, checkFailed, "no in-force proof at "+d.opts.InForcePath+", so it is not known that Claude Code applies the drop-in: "+prove+
			", then oba kaya install")
	case proof.DropInSha256 != install.DropInSha256(d.dropIn):
		d.report.add(scopeMachine, name, checkFailed, "the in-force proof names another drop-in than the one installed: "+prove)
	default:
		d.inForce = true
		d.report.add(scopeMachine, name, checkOK, "a probe session proved it in force at "+proof.ProvedAt.UTC().Format(time.RFC3339))
	}
}

func (d *doctor) checkUserSettings() {
	const name = "user settings"
	path := install.ClaudeSettingsPath(d.opts)
	count, err := install.UserSettingsKayaHooks(d.opts)
	switch {
	case err != nil:
		d.report.add(scopeMachine, name, checkFailed, err.Error())
	case count == 0:
		d.report.add(scopeMachine, name, checkOK, path+" holds no Kaya hook")
	case d.inForce:
		d.report.add(scopeMachine, name, checkFailed, fmt.Sprintf("%s still holds %d of Kaya's hooks, which run a second time beside the drop-in: "+
			"run oba kaya install, which removes them now that the drop-in is proved in force", path, count))
	default:
		d.report.add(scopeMachine, name, checkSkipped, fmt.Sprintf("%s keeps %d of Kaya's hooks until the drop-in is proved in force", path, count))
	}
}

// print writes the report for a terminal, grouped by scope.
func (r doctorReport) print(w io.Writer) {
	titles := map[string]string{scopeMachine: "Machine", scopeCheckout: "Checkout", scopeProbe: "Probe"}
	if r.Checkout != nil {
		titles[scopeCheckout] = "Checkout " + r.Checkout.Root
	}
	labels := map[string]string{checkOK: "ok", checkFailed: "FAIL", checkSkipped: "skip"}
	failed := 0
	for _, scope := range []string{scopeMachine, scopeCheckout, scopeProbe} {
		printed := false
		for _, check := range r.Checks {
			if check.Scope != scope {
				continue
			}
			if !printed {
				_, _ = fmt.Fprintln(w, titles[scope]+":")
				printed = true
			}
			if check.Status == checkFailed {
				failed++
			}
			_, _ = fmt.Fprintf(w, "  %-4s %s: %s\n", labels[check.Status], check.Name, check.Detail)
		}
	}
	if r.Cutover != nil {
		if len(r.Cutover.Paths) == 0 {
			_, _ = fmt.Fprintln(w, "Not cut over yet: git tracks nothing the cutover pull request deletes.")
		} else {
			_, _ = fmt.Fprintf(w, "Not cut over yet: the cutover pull request deletes these %d tracked paths:\n", len(r.Cutover.Paths))
			for _, path := range r.Cutover.Paths {
				_, _ = fmt.Fprintln(w, "  "+path)
			}
			_, _ = fmt.Fprintln(w, "Delete them in the cutover branch with:")
			_, _ = fmt.Fprintln(w, "  "+r.Cutover.Command)
		}
		if r.Cutover.Incomplete != "" {
			_, _ = fmt.Fprintln(w, "Warning: "+r.Cutover.Incomplete)
		}
	}
	if failed == 0 {
		_, _ = fmt.Fprintln(w, "No check failed.")
		return
	}
	_, _ = fmt.Fprintf(w, "%d check(s) failed.\n", failed)
}
