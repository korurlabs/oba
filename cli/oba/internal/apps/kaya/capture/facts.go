package capture

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"regexp"
	"strings"
	"time"
)

// gitTimeout bounds one git call of a hook, which runs only on SessionStart, Stop and SessionEnd.
const gitTimeout = 5 * time.Second

// accountFields are the fields of `oauthAccount` in .claude.json that Kaya records. Nothing else
// of that file, which holds other private data, is ever copied.
var accountFields = []string{"emailAddress", "organizationName", "organizationUuid", "billingType", "organizationType", "seatTier"}

var (
	// userInfo is the user name and password an https remote may carry; they never leave the
	// machine.
	userInfo   = regexp.MustCompile(`^([a-zA-Z][a-zA-Z0-9+.-]*://)[^/@]*@`)
	remotePart = regexp.MustCompile(`[/:]`)
)

// hostname names the machine in session.json; tests fix it.
var hostname = os.Hostname

// git is the trimmed stdout of a git command run in dir, "" when git fails. A git that cannot be
// started at all is reported as a warning, since the session's branch and git author are then lost.
func git(dir string, warn func(string, ...any), args ...string) string {
	ctx, cancel := context.WithTimeout(context.Background(), gitTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, "git", append([]string{"-C", dir}, args...)...) // #nosec G204 -- fixed git subcommands on the session's directory
	cmd.Env = append(os.Environ(), "GIT_OPTIONAL_LOCKS=0", "LC_ALL=C")
	cmd.WaitDelay = 100 * time.Millisecond
	out, err := cmd.Output()
	var exitErr *exec.ExitError
	if err != nil && !errors.As(err, &exitErr) {
		warn("git %s in %s failed: %v", strings.Join(args, " "), dir, err)
		return ""
	}
	if err != nil {
		// rule-10-no-op -- reason: a non-zero exit is an answer, such as a branch without a commit or no user.email.
		return ""
	}
	return strings.TrimSpace(string(out))
}

// ExistingDir is the nearest existing directory at or above dir, "" when there is none.
func ExistingDir(dir string) string {
	d, err := filepath.Abs(dir)
	if err != nil {
		return ""
	}
	for {
		if info, err := os.Stat(d); err == nil && info.IsDir() {
			return d
		}
		parent := filepath.Dir(d)
		if parent == d {
			return ""
		}
		d = parent
	}
}

// realPath resolves links; a path that does not exist stays as written, cleaned.
func realPath(path string) string {
	if resolved, err := filepath.EvalSymlinks(path); err == nil {
		return resolved
	}
	return filepath.Clean(path)
}

// mainCheckout is the root of the main checkout of the repository that holds cwd, "" when none.
// It is read from the .git entries on disk so that a tool event needs no git process: a linked
// worktree's .git file names its git directory, which names the shared one in `commondir`. git
// itself is the fallback for any other layout.
func mainCheckout(cwd string, warn func(string, ...any)) (string, error) {
	start := ExistingDir(cwd)
	for d := start; d != ""; {
		dotGit := filepath.Join(d, ".git")
		info, err := os.Stat(dotGit)
		if err == nil && info.IsDir() {
			return realPath(d), nil
		}
		if err == nil {
			root, err := checkoutOfGitFile(d, dotGit)
			if err != nil || root != "" {
				return root, err
			}
			break
		}
		parent := filepath.Dir(d)
		if parent == d {
			break
		}
		d = parent
	}
	if start == "" {
		return "", nil
	}
	if out := git(start, warn, "rev-parse", "--path-format=absolute", "--git-common-dir"); out != "" && filepath.Base(out) == ".git" {
		return filepath.Dir(realPath(out)), nil
	}
	if out := git(start, warn, "rev-parse", "--show-toplevel"); out != "" {
		return realPath(out), nil
	}
	return "", nil
}

// checkoutOfGitFile follows a worktree's `.git` file to the main checkout; "" when the file does
// not lead to one.
func checkoutOfGitFile(dir, dotGit string) (string, error) {
	line, err := firstLine(dotGit)
	if err != nil {
		return "", fmt.Errorf("cannot read %s: %w", dotGit, err)
	}
	gitDir, ok := strings.CutPrefix(line, "gitdir:")
	if !ok {
		return "", nil
	}
	gitDir = strings.TrimSpace(gitDir)
	if !filepath.IsAbs(gitDir) {
		gitDir = filepath.Join(dir, gitDir)
	}
	gitDir = realPath(gitDir)
	common := gitDir
	commonFile := filepath.Join(gitDir, "commondir")
	if info, err := os.Stat(commonFile); err == nil && !info.IsDir() {
		rel, err := firstLine(commonFile)
		if err != nil {
			return "", fmt.Errorf("cannot read %s: %w", commonFile, err)
		}
		if !filepath.IsAbs(rel) {
			rel = filepath.Join(gitDir, rel)
		}
		common = realPath(rel)
	}
	if filepath.Base(common) == ".git" {
		return filepath.Dir(common), nil
	}
	return "", nil
}

func firstLine(path string) (string, error) {
	data, err := os.ReadFile(path) // #nosec G304 -- a git pointer file of the checkout
	if err != nil {
		return "", err
	}
	line, _, _ := strings.Cut(string(data), "\n")
	return strings.TrimSpace(line), nil
}

// CleanRemote is the remote without the user name and password a URL remote may carry, for every
// scheme. The hook passes the remote that put the checkout on the repository list, in the form
// that matched: as git reports it, or with its ssh host alias resolved when only the resolved host
// is listed. The sender sends a recorded remote only while the cached list holds it, as recorded or
// with its alias resolved, so a remote never leaves the machine unlisted.
func CleanRemote(remote string) string {
	return userInfo.ReplaceAllString(strings.TrimSpace(remote), "$1")
}

// RepositoryName is `village` for `git@github.com:korurlabs/village.git` or
// `https://github.com/korurlabs/village`; "" when the remote names none.
func RepositoryName(remote string) string {
	parts := remotePart.Split(strings.TrimRight(remote, "/"), -1)
	tail := strings.TrimSuffix(parts[len(parts)-1], ".git")
	return cutRunes(tail, 100)
}

// claudeAccount is the six account fields of `oauthAccount` in .claude.json, nil when Claude Code
// signs in with an API key or the file is missing. A damaged file is logged and gives no account.
func claudeAccount() map[string]any {
	dir := os.Getenv("CLAUDE_CONFIG_DIR")
	if dir == "" {
		home, err := os.UserHomeDir()
		if err != nil {
			logOrStderr(fmt.Sprintf("no home directory, no Claude account reported: %v", err))
			return nil
		}
		dir = home
	}
	path := filepath.Join(dir, ".claude.json")
	var data map[string]any
	if _, err := readJSON(path, &data); err != nil {
		logOrStderr(fmt.Sprintf("%s unreadable, no Claude account reported: %v", path, err))
		return nil
	}
	account := asObject(data["oauthAccount"])
	picked := map[string]any{}
	for _, key := range accountFields {
		if value := stringOf(account[key]); value != "" {
			picked[key] = value
		}
	}
	if len(picked) == 0 {
		return nil
	}
	return picked
}

// logOrStderr writes a line to recorder.log, or to stderr when the log cannot be written, so the
// reason is never lost.
func logOrStderr(message string) {
	if err := Log(message); err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "kaya recorder: %v: %s\n", err, message)
	}
}

// updateSession keeps session.json, the facts of the session the sender reports beside its
// events: machine, Claude account (read once per session), Claude Code version, model, remote
// and git author email. The remote is the one the caller recognised the checkout by, kept from
// the first event that brings one; git runs only on SessionStart, Stop and SessionEnd.
func updateSession(dir, sessionID string, event, in map[string]any, cwd, root, remote string, warn func(string, ...any)) error {
	path := filepath.Join(dir, SessionFile)
	var session map[string]any
	if _, err := readJSON(path, &session); err != nil {
		warn("%s is unreadable, its facts are read again: %v", path, err)
		session = nil
	}
	before := cloneMap(session)
	if session == nil || session["sessionId"] != sessionID {
		machine, err := hostname()
		if err != nil {
			warn("the machine name is unknown: %v", err)
		}
		var account any
		if picked := claudeAccount(); picked != nil {
			account = picked
		}
		var sessionCwd any
		if cwd := stringOf(event["cwd"]); cwd != "" {
			sessionCwd = cwd
		}
		session = map[string]any{"sessionId": sessionID, "machineName": machine, "account": account, "cwd": sessionCwd}
	}
	name := stringOf(event["event"])
	if version := stringOf(in["version"]); version != "" {
		session["claudeCodeVersion"] = version
	}
	if model := stringOf(event["model"]); name == sessionStart && !truthy(event["agent_id"]) && model != "" {
		session["model"] = model
	}
	if remote := CleanRemote(remote); remote != "" && stringOf(session["remoteUrl"]) == "" {
		session["remoteUrl"] = remote
		var repository any
		if name := RepositoryName(remote); name != "" {
			repository = name
		}
		session["repositoryName"] = repository
	}
	if branchEvents[name] {
		where := ExistingDir(cwd)
		if where == "" {
			where = root
		}
		if _, known := session["gitAuthorEmail"]; name == sessionStart || !known {
			var email any
			if value := git(where, warn, "config", "user.email"); value != "" {
				email = value
			}
			session["gitAuthorEmail"] = email
		}
		model, version := transcriptFacts(stringOf(in["transcript_path"]))
		if model != "" {
			session["model"] = model
		}
		if version != "" {
			session["claudeCodeVersion"] = version
		}
	}
	if reflect.DeepEqual(session, before) {
		return nil
	}
	return writeJSON(path, session)
}

func cloneMap(m map[string]any) map[string]any {
	if m == nil {
		return nil
	}
	out := make(map[string]any, len(m))
	for key, value := range m {
		out[key] = value
	}
	return out
}
