package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strings"
	"syscall"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
)

// Kind says whether Kaya delivers the config of a directory's checkout.
type Kind int

const (
	// NotRepository: the directory is in no git checkout.
	NotRepository Kind = iota
	// NotKaya: no remote of the checkout is on the repository list; its config is its own, and
	// nothing of it, its remotes included, is sent to Kaya.
	NotKaya
	// NotCutOver: a remote of the checkout is on the repository list, but the checkout still syncs
	// its agent config from elsewhere (.agents/.managed.json); its delivered paths are tracked in
	// git and must not be replaced.
	NotCutOver
	// Delivered: Kaya delivers the checkout's config.
	Delivered
)

// String names the kind as status prints it.
func (k Kind) String() string {
	switch k {
	case NotRepository:
		return "NotRepository"
	case NotKaya:
		return "NotKaya"
	case NotCutOver:
		return "NotCutOver"
	case Delivered:
		return "Delivered"
	}
	return fmt.Sprintf("Kind(%d)", int(k))
}

// ConfigFile is the committed file that named a checkout's repository to Kaya before Kaya
// recognised checkouts by their remotes. It is no longer read; a tracked one is a leftover.
const ConfigFile = "kaya.yaml"

// Checkout is one working tree; a linked worktree is a checkout of its own.
type Checkout struct {
	Root string
	Kind Kind
	// Remote is the remote on the repository list that names the checkout's repository,
	// normalised (host/owner/name); empty in a NotKaya checkout.
	Remote string
	// RemoteName is the git remote it came from.
	RemoteName string
	// Organization is the organization of the repository list, which every release of the
	// checkout must be signed for.
	Organization string
	// Unresolved names the ssh hosts of the checkout's remotes that ssh could not resolve while
	// they were matched against the list; such a remote may name a listed repository unseen.
	Unresolved []string
	// requestRemote is the remote as the API is asked about it: as git reports it, without user
	// info, its ssh host alias resolved when only the resolved form is on the list.
	requestRemote string
}

// FindCheckout walks up from dir to the nearest directory holding .git and recognises it by its
// remotes. When the repository list or the remotes cannot be read, it returns the checkout as
// NotKaya together with the error, so a caller that needs only the root can go on.
func FindCheckout(dir string) (*Checkout, error) {
	abs, err := filepath.Abs(dir)
	if err != nil {
		return nil, fmt.Errorf("resolve %s: %w", dir, err)
	}
	for current := abs; ; {
		if _, err := os.Lstat(filepath.Join(current, ".git")); err == nil {
			return openCheckout(current)
		}
		parent := filepath.Dir(current)
		if parent == current {
			return &Checkout{Root: abs, Kind: NotRepository}, nil
		}
		current = parent
	}
}

func openCheckout(root string) (*Checkout, error) {
	c := &Checkout{Root: root, Kind: NotKaya}
	list, err := LoadRepositoryList()
	if err != nil {
		return c, err
	}
	if len(list.Remotes) == 0 {
		// Without a list no remote can match, and git need not run.
		return c, nil
	}
	remotes, err := gitRemotes(root)
	if err != nil {
		return c, err
	}
	c.recognize(list, remotes, seed.NewRemoteResolver().Lookup)
	if c.Remote == "" {
		return c, nil
	}
	c.Kind = Delivered
	c.Organization = list.OrganizationID
	if _, err := os.Lstat(filepath.Join(root, ".agents", ".managed.json")); err == nil {
		c.Kind = NotCutOver
	}
	return c, nil
}

// gitRemote is one url of a remote of the checkout's git config.
type gitRemote struct {
	name, url string
}

// gitRemotes lists the urls of the remotes in the checkout's own git config, origin first and
// the other remotes in name order, each remote's urls as configured. Global, system and
// command-line config are never read, and the variables that point git at another repository
// are dropped, so only the checkout's own remotes count.
func gitRemotes(root string) ([]gitRemote, error) {
	cmd := exec.Command("git", "-C", root, "config", "--local", "-z", "--get-regexp", `^remote\..+\.url$`) // #nosec G204 -- fixed git subcommand on the checkout
	cmd.Env = withoutGitVars(os.Environ())
	out, err := cmd.Output()
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) && exitErr.ExitCode() == 1 {
		// git config exits 1 when no key matches: the checkout has no remote.
		return nil, nil
	}
	if err != nil {
		reason := err.Error()
		if exitErr != nil && len(exitErr.Stderr) > 0 {
			reason = strings.TrimSpace(string(exitErr.Stderr))
		}
		return nil, fmt.Errorf("read the remotes of %s: %s", root, reason)
	}
	var remotes []gitRemote
	for _, entry := range strings.Split(string(out), "\x00") {
		key, value, ok := strings.Cut(entry, "\n")
		name, isRemote := strings.CutPrefix(key, "remote.")
		name, isURL := strings.CutSuffix(name, ".url")
		if !ok || !isRemote || !isURL || name == "" {
			continue
		}
		remotes = append(remotes, gitRemote{name: name, url: value})
	}
	slices.SortStableFunc(remotes, func(a, b gitRemote) int {
		switch {
		case a.name == b.name:
			return 0
		case a.name == "origin":
			return -1
		case b.name == "origin":
			return 1
		}
		return strings.Compare(a.name, b.name)
	})
	return remotes, nil
}

// repositoryGitVars are the variables that tie git to one repository, as `git rev-parse
// --local-env-vars` lists them; git drops the same ones when it enters another repository.
var repositoryGitVars = []string{
	"GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_CONFIG", "GIT_CONFIG_PARAMETERS", "GIT_CONFIG_COUNT",
	"GIT_OBJECT_DIRECTORY", "GIT_DIR", "GIT_WORK_TREE", "GIT_IMPLICIT_WORK_TREE", "GIT_GRAFT_FILE",
	"GIT_INDEX_FILE", "GIT_NO_REPLACE_OBJECTS", "GIT_REPLACE_REF_BASE", "GIT_PREFIX", "GIT_SHALLOW_FILE",
	"GIT_COMMON_DIR",
}

// withoutGitVars drops the repository variables, such as the GIT_DIR a git hook runs under,
// which would make git read another repository's config.
func withoutGitVars(env []string) []string {
	kept := make([]string, 0, len(env))
	for _, entry := range env {
		name, _, _ := strings.Cut(entry, "=")
		if !slices.Contains(repositoryGitVars, name) {
			kept = append(kept, entry)
		}
	}
	return kept
}

// recognize names the checkout's repository by the first remote on the list, in the order of
// remotes. A remote counts as written, without user info, and else with its ssh host alias
// resolved through lookup, so that a clone over an alias is recognised as a clone over the host.
func (c *Checkout) recognize(list RepositoryList, remotes []gitRemote, lookup func(string) (string, error)) {
	resolver := &seed.RemoteResolver{Lookup: func(host string) (string, error) {
		name, err := lookup(host)
		if err != nil {
			c.Unresolved = append(c.Unresolved, fmt.Sprintf("%s (%v)", host, err))
		}
		return name, err
	}}
	for _, remote := range remotes {
		cleaned := seed.CleanRemote(strings.TrimSpace(remote.url))
		if normalized := seed.NormalizeRemote(cleaned); list.Has(normalized) {
			c.Remote, c.RemoteName, c.requestRemote = normalized, remote.name, cleaned
			return
		}
		// rule-10-no-op -- reason: the warning repeats the lookup error, which the Lookup above already keeps in c.Unresolved.
		resolved, _ := resolver.Resolve(cleaned)
		if resolved == cleaned {
			continue
		}
		if normalized := seed.NormalizeRemote(resolved); list.Has(normalized) {
			c.Remote, c.RemoteName, c.requestRemote = normalized, remote.name, resolved
			return
		}
	}
}

// Paths of the checkout's local delivery state; .kaya/ is gitignored after the cutover.
func (c *Checkout) kayaDir() string     { return filepath.Join(c.Root, ".kaya") }
func (c *Checkout) versionsDir() string { return filepath.Join(c.kayaDir(), "versions") }
func (c *Checkout) versionDir(number int) string {
	return filepath.Join(c.versionsDir(), fmt.Sprint(number))
}
func (c *Checkout) releasePath(number int) string {
	return filepath.Join(c.kayaDir(), "releases", fmt.Sprintf("%d.json", number))
}
func (c *Checkout) currentLink() string { return filepath.Join(c.kayaDir(), "current") }
func (c *Checkout) sessionsDir() string { return filepath.Join(c.kayaDir(), "sessions") }
func (c *Checkout) statePath() string   { return filepath.Join(c.kayaDir(), "state.json") }

// ReleaseRecordPath is the checkout file that names the active release, for agents and tools.
const ReleaseRecordPath = ".agents/.kaya-release.json"

// Name is how messages name the checkout's repository.
func (c *Checkout) Name(state State) string {
	if state.RemoteURL != "" {
		return state.RemoteURL
	}
	return filepath.Base(c.Root)
}

// ensureKayaDir creates .kaya/ and refuses one that is not a real directory, since every write
// below it would otherwise follow a link out of the checkout.
func (c *Checkout) ensureKayaDir() error {
	for _, dir := range []string{c.kayaDir(), c.versionsDir(), filepath.Dir(c.releasePath(0)), c.sessionsDir()} {
		info, err := os.Lstat(dir)
		if errors.Is(err, fs.ErrNotExist) {
			if err := os.Mkdir(dir, 0o750); err != nil {
				return fmt.Errorf("create %s: %w", dir, err)
			}
			continue
		}
		if err != nil {
			return fmt.Errorf("inspect %s: %w", dir, err)
		}
		if !info.IsDir() {
			return fmt.Errorf("%s is not a directory; remove it and sync again", dir)
		}
	}
	return nil
}

// Remotes gives the remote sent to the API and the normalised remote on the repository list that
// a manifest is signed for. A checkout without a listed remote has neither, so nothing of it is
// ever sent to Kaya.
func (c *Checkout) Remotes() (request, normalized string, err error) {
	if c.Remote == "" {
		return "", "", fmt.Errorf("no remote of %s is on the organization's repository list, so Kaya does not know its repository", c.Root)
	}
	return c.requestRemote, c.Remote, nil
}

// State is the checkout's delivery state in .kaya/state.json.
type State struct {
	Organization  string `json:"organization,omitempty"`
	RequestRemote string `json:"requestRemote,omitempty"`
	RemoteURL     string `json:"remoteUrl,omitempty"`
	ETag          string `json:"etag,omitempty"`
	// Active is the release the delivered paths point at; 0 before the first switch.
	Active int `json:"active,omitempty"`
	// Staged is a verified, built release that waits for the checkout's live sessions to end.
	Staged int `json:"staged,omitempty"`
	// Highest is the newest release number ever verified here; an older one is refused.
	Highest int `json:"highest,omitempty"`
	// Latest is the newest release the server named at the last contact.
	Latest    int    `json:"latest,omitempty"`
	CheckedAt string `json:"checkedAt,omitempty"`
	// LastSessionRelease is the release the previous session started on, for the list of
	// changed paths a session start prints.
	LastSessionRelease int `json:"lastSessionRelease,omitempty"`
	// LessonsETag names the lessons .kaya/lessons.md was written from.
	LessonsETag string `json:"lessonsEtag,omitempty"`
	// LessonsSeenAt is the newest updatedAt Kaya answered, not a time of this machine: Kaya
	// compares it with its own clock, so a skewed laptop can neither miss nor repeat a lesson.
	LessonsSeenAt string `json:"lessonsSeenAt,omitempty"`
}

// LoadState reads .kaya/state.json; a missing file is an empty state.
func (c *Checkout) LoadState() (State, error) {
	var state State
	data, err := os.ReadFile(c.statePath())
	if errors.Is(err, fs.ErrNotExist) {
		return state, nil
	}
	if err != nil {
		return state, fmt.Errorf("read %s: %w", c.statePath(), err)
	}
	if err := json.Unmarshal(data, &state); err != nil {
		return state, fmt.Errorf("%s is damaged: %w", c.statePath(), err)
	}
	return state, nil
}

// SaveState writes .kaya/state.json by rename.
func (c *Checkout) SaveState(state State) error {
	if err := c.ensureKayaDir(); err != nil {
		return err
	}
	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return fmt.Errorf("encode the checkout state: %w", err)
	}
	return writeFileAtomic(c.statePath(), append(data, '\n'), 0o600)
}

// Lock takes the checkout's delivery lock, so that two launches never build or switch at once.
// It waits until ctx ends.
func (c *Checkout) Lock(ctx context.Context) (func(), error) {
	if err := c.ensureKayaDir(); err != nil {
		return nil, err
	}
	path := filepath.Join(c.kayaDir(), "lock")
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed path below the checkout's .kaya
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	for {
		err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB) // #nosec G115 -- a file descriptor fits an int
		if err == nil {
			return func() {
				_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN) // #nosec G115 -- a file descriptor fits an int
				_ = file.Close()
			}, nil
		}
		if !errors.Is(err, syscall.EWOULDBLOCK) {
			_ = file.Close()
			return nil, fmt.Errorf("lock %s: %w", path, err)
		}
		select {
		case <-ctx.Done():
			_ = file.Close()
			return nil, fmt.Errorf("another kaya command holds %s: %w", path, ctx.Err())
		case <-time.After(20 * time.Millisecond):
		}
	}
}

// writeFileAtomic writes data to a temporary file next to path and renames it over path, so a
// reader sees the old or the new content, never a part.
func writeFileAtomic(path string, data []byte, perm fs.FileMode) error {
	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, "."+filepath.Base(path)+".tmp-*")
	if err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	name := tmp.Name()
	_, writeErr := tmp.Write(data)
	closeErr := tmp.Close()
	if err := errors.Join(writeErr, closeErr); err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("write %s: %w", path, err)
	}
	if err := os.Chmod(name, perm); err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("write %s: %w", path, err)
	}
	if err := os.Rename(name, path); err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}
