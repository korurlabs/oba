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
	"strings"
	"syscall"
	"time"

	"gopkg.in/yaml.v3"

	"github.com/korurlabs/kaya-cli/internal/seed"
)

// Kind says whether Kaya delivers the config of a directory's checkout.
type Kind int

const (
	// NotRepository: the directory is in no git checkout.
	NotRepository Kind = iota
	// NotKaya: the checkout has no kaya.yaml; its config is its own.
	NotKaya
	// NotCutOver: the checkout has kaya.yaml but still syncs from korurlabs/agents
	// (.agents/.managed.json); its delivered paths are tracked in git and must not be replaced.
	NotCutOver
	// Delivered: Kaya delivers the checkout's config.
	Delivered
)

// ConfigFile is the committed file that hands a checkout's agent config to Kaya.
const ConfigFile = "kaya.yaml"

// CheckoutConfig is kaya.yaml. Both fields are optional.
type CheckoutConfig struct {
	// RemoteURL names the repository when the origin remote does not, for example in a fork.
	RemoteURL string `yaml:"remoteUrl"`
	// Organization pins the organization id a release must be signed for. Without it the first
	// verified release pins it in .kaya/state.json.
	Organization string `yaml:"organization"`
}

// Checkout is one working tree; a linked worktree is a checkout of its own.
type Checkout struct {
	Root   string
	Config CheckoutConfig
	Kind   Kind
}

// FindCheckout walks up from dir to the nearest directory holding .git.
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
	data, err := os.ReadFile(filepath.Join(root, ConfigFile)) // #nosec G304 -- the checkout's own committed file
	if errors.Is(err, fs.ErrNotExist) {
		return c, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", filepath.Join(root, ConfigFile), err)
	}
	if err := yaml.Unmarshal(data, &c.Config); err != nil {
		return nil, fmt.Errorf("%s: %w", filepath.Join(root, ConfigFile), err)
	}
	c.Kind = Delivered
	if _, err := os.Lstat(filepath.Join(root, ".agents", ".managed.json")); err == nil {
		c.Kind = NotCutOver
	}
	return c, nil
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

// Remotes gives the remote sent to the API (the origin as git reports it, without user info and
// with an ssh host alias resolved) and the normalised remote a manifest is signed for.
func (c *Checkout) Remotes() (request, normalized string, err error) {
	raw := strings.TrimSpace(c.Config.RemoteURL)
	if raw == "" {
		cmd := exec.Command("git", "-C", c.Root, "remote", "get-url", "origin") // #nosec G204 -- fixed git subcommand on the checkout
		out, err := cmd.Output()
		if err != nil {
			return "", "", fmt.Errorf("read the origin remote of %s (or set remoteUrl in %s): %w", c.Root, ConfigFile, err)
		}
		raw = strings.TrimSpace(string(out))
	}
	request, _ = seed.NewRemoteResolver().Resolve(seed.CleanRemote(raw))
	normalized = seed.NormalizeRemote(request)
	if normalized == "" {
		return "", "", fmt.Errorf("the remote %s is not a git remote Kaya accepts", request)
	}
	return request, normalized, nil
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
