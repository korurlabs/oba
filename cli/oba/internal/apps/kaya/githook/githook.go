// Package githook is oba kaya git-hook: the git hooks of config delivery, which oba kaya install
// registers in the user's global git config, so that git runs them in every repository beside
// the repository's own hooks. They act only in a checkout Kaya delivers to. pre-commit keeps the
// delivered paths out of every commit; post-checkout puts the active release in place in a new
// worktree, and again after a branch switch, since a checkout overwrites ignored files.
package githook

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

// The git hook events Run handles.
const (
	PreCommit    = "pre-commit"
	PostCheckout = "post-checkout"
)

// Events are the git hook events Run handles.
var Events = []string{PreCommit, PostCheckout}

// onlineBudget bounds the machine token lookup and every API call of a new worktree's delivery
// together: past it the worktree gets the release this laptop already verified.
const onlineBudget = 3 * time.Second

// guardedDirs are the top-level folders of agent config. Kaya delivers into them, so git must not
// track anything below them in a delivered checkout, whether the active release names the path
// or not.
var guardedDirs = []string{".agents", ".claude", ".kaya"}

// Run runs the git hook event with the arguments git passed it, in the working directory git runs
// hooks in, and returns the exit status for git. Only pre-commit refusing a staged delivered path
// returns non-zero: every failure of the hook itself goes to stderr, and git goes on.
func Run(ctx context.Context, event string, args []string, stderr io.Writer) int {
	switch event {
	case PreCommit:
		return preCommit(ctx, stderr)
	case PostCheckout:
		if err := postCheckout(ctx, args, stderr); err != nil {
			warn(stderr, event, fmt.Errorf("%w; run oba kaya sync in this checkout", err))
		}
		return 0
	}
	warn(stderr, event, errors.New("unknown git hook; run oba kaya install again to set Kaya's git hooks"))
	return 0
}

// warn reports a failure of the hook itself, which never stops git.
func warn(stderr io.Writer, event string, err error) {
	_, _ = fmt.Fprintf(stderr, "oba kaya git-hook %s: %v\n", event, err)
}

// deliveredCheckout is the checkout of the working directory when Kaya delivers to it, else nil.
// A recognition error comes back with a nil checkout, for the caller to report.
func deliveredCheckout() (*delivery.Checkout, error) {
	checkout, err := delivery.CurrentCheckout()
	if err != nil {
		return nil, err
	}
	if checkout.Kind != delivery.Delivered {
		return nil, nil
	}
	return checkout, nil
}

// preCommit refuses a commit that stages any change but a deletion to a path the active release
// delivers or to a path below .agents/, .claude/ or .kaya/: a force-added delivered file, or a
// tracked file turned into Kaya's link. A deletion passes, so the commit that stops tracking
// agent config goes through.
func preCommit(ctx context.Context, stderr io.Writer) int {
	checkout, err := deliveredCheckout()
	if err != nil {
		warn(stderr, PreCommit, fmt.Errorf("%w; the commit was not checked for delivered paths", err))
		return 0
	}
	if checkout == nil {
		return 0
	}
	staged, err := stagedPaths(ctx, checkout.Root)
	if err != nil {
		warn(stderr, PreCommit, fmt.Errorf("%w; the commit was not checked for delivered paths", err))
		return 0
	}
	if len(staged) == 0 {
		return 0
	}
	manifest, err := activeManifest(ctx, checkout)
	if err != nil {
		warn(stderr, PreCommit, fmt.Errorf("%w; only the paths below %s were checked", err, strings.Join(guardedDirs, "/, ")+"/"))
	}
	var refused []string
	for _, path := range staged {
		if underGuardedDir(path) || (manifest != nil && manifest.Delivers(path)) {
			refused = append(refused, path)
		}
	}
	if len(refused) == 0 {
		return 0
	}
	var msg strings.Builder
	msg.WriteString("oba kaya: the commit stages paths that Kaya delivers to this checkout, which git must not track:\n")
	for _, path := range refused {
		msg.WriteString("  " + path + "\n")
	}
	msg.WriteString("Take them out of git and keep the delivered files with:\n")
	for _, path := range refused {
		msg.WriteString("  git rm -r --cached -- " + shellQuote(path) + "\n")
	}
	msg.WriteString("then commit again.\n")
	_, _ = io.WriteString(stderr, msg.String())
	return 1
}

// stagedPaths lists every staged change but a deletion, type changes included, relative to the
// checkout's root. git's environment stays as git gave it, since a commit of named paths or with
// -a stages into a temporary index that GIT_INDEX_FILE names.
func stagedPaths(ctx context.Context, root string) ([]string, error) {
	cmd := exec.CommandContext(ctx, "git", "diff", "--cached", "--name-only", "-z", "--no-renames", "--diff-filter=d")
	cmd.Dir = root
	var stdout, stderr bytes.Buffer
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	if err := cmd.Run(); err != nil {
		return nil, fmt.Errorf("list the staged paths: %w: %s", err, strings.TrimSpace(stderr.String()))
	}
	var paths []string
	for _, path := range strings.Split(stdout.String(), "\x00") {
		if path != "" {
			paths = append(paths, path)
		}
	}
	return paths, nil
}

// activeManifest is the manifest of the checkout's active release, read and verified from local
// files only; nil when no release is active.
func activeManifest(ctx context.Context, checkout *delivery.Checkout) (*delivery.Manifest, error) {
	d, err := delivery.Open(ctx, checkout, false)
	if err != nil {
		return nil, err
	}
	active, _, err := d.ActiveRelease()
	if err != nil || active == nil {
		return nil, err
	}
	return &active.Manifest, nil
}

// underGuardedDir reports whether path is one of the agent config folders or below one, ignoring
// case as a case-insensitive file system does.
func underGuardedDir(path string) bool {
	top, _, _ := strings.Cut(path, "/")
	for _, dir := range guardedDirs {
		if strings.EqualFold(top, dir) {
			return true
		}
	}
	return false
}

// postCheckout acts on a branch checkout, which also runs for a new worktree and a clone; a
// checkout of files (third argument 0) changes no branch and leaves delivery alone. A checkout
// with an active release gets it placed again; one without, a new worktree, gets delivered.
func postCheckout(ctx context.Context, args []string, stderr io.Writer) error {
	if len(args) < 3 || args[2] != "1" {
		return nil
	}
	checkout, err := deliveredCheckout()
	if err != nil || checkout == nil {
		return err
	}
	if _, err := os.Lstat(filepath.Join(checkout.Root, ".kaya", "current")); err == nil {
		return placeAgain(ctx, checkout, stderr)
	} else if !errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("inspect the active release of %s: %w", checkout.Root, err)
	}
	return deliverNew(ctx, checkout, stderr)
}

// placeAgain puts every delivered path of the active release back from local files, without
// calling Kaya: git may have overwritten or removed them while it switched branches.
func placeAgain(ctx context.Context, checkout *delivery.Checkout, stderr io.Writer) error {
	d, err := delivery.Open(ctx, checkout, false)
	if err != nil {
		return err
	}
	lockCtx, cancel := delivery.LockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, delivery.Pin)
	if err != nil {
		return err
	}
	return placed(out, stderr)
}

// deliverNew syncs a checkout that holds no release yet, a new worktree above all, within
// onlineBudget. When Kaya is not reached or its newest release is not taken, it places the
// release the main worktree has active instead, verified again here.
func deliverNew(ctx context.Context, checkout *delivery.Checkout, stderr io.Writer) error {
	online, cancelOnline := context.WithTimeout(ctx, onlineBudget)
	defer cancelOnline()
	m, err := delivery.ConnectMachine(online)
	if err != nil {
		m = delivery.Machine{Err: err}
	}
	d, err := delivery.OpenWith(checkout, m)
	if err != nil {
		return err
	}
	if deadline, ok := online.Deadline(); ok {
		d.OnlineTimeout = time.Until(deadline)
	}
	lockCtx, cancel := delivery.LockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, delivery.Pin)
	if err != nil {
		return err
	}
	if out.Active > 0 || out.Offline == nil {
		if out.Active > 0 {
			_, _ = fmt.Fprintf(stderr, "oba kaya: config release %d placed in %s.\n", out.Active, checkout.Root)
		}
		return placed(out, stderr)
	}
	return deliverHeld(lockCtx, d, out.Offline, stderr)
}

// deliverHeld places the release the main worktree has active: the release file comes from that
// worktree's .kaya/releases/, every blob from this laptop's cache, and the release passes the
// same signature, origin and path checks as one fetched from Kaya.
func deliverHeld(ctx context.Context, d *delivery.Deliverer, offline error, stderr io.Writer) error {
	checkout := d.Checkout
	unreached := fmt.Sprintf("no release came from Kaya (%v)", offline)
	main, err := mainWorktree(ctx, checkout.Root)
	if err != nil {
		return fmt.Errorf("%s, and %w", unreached, err)
	}
	if main == "" || sameDir(main, checkout.Root) {
		return fmt.Errorf("%s, and no other worktree of this repository holds a release", unreached)
	}
	mainState, err := (&delivery.Checkout{Root: main}).LoadState()
	if err != nil {
		return fmt.Errorf("%s, and %w", unreached, err)
	}
	if mainState.Active == 0 {
		return fmt.Errorf("%s, and the main worktree %s has no active release", unreached, main)
	}
	release, err := readHeldRelease(main, mainState.Active)
	if err != nil {
		return fmt.Errorf("%s, and %w", unreached, err)
	}
	d.Source, d.SourceError = heldSource{release: release, from: main}, nil
	out, err := d.Sync(ctx, delivery.Pin)
	if err != nil {
		return err
	}
	if out.Active == 0 {
		for _, warning := range out.Warnings {
			_, _ = fmt.Fprintln(stderr, "oba kaya:", warning)
		}
		if out.Offline != nil {
			return fmt.Errorf("%s, and release %d of %s was not taken: %w", unreached, release.Number, main, out.Offline)
		}
		return fmt.Errorf("%s, and release %d of %s was not placed", unreached, release.Number, main)
	}
	if err := keepLastContact(checkout, mainState); err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
	}
	_, _ = fmt.Fprintf(stderr, "oba kaya: %s; config release %d, which %s has active, placed in %s.\n",
		unreached, out.Active, main, checkout.Root)
	return placed(out, stderr)
}

// keepLastContact gives the checkout the main worktree's last contact with Kaya and the newest
// release Kaya named then: the sync from the main worktree's release reached no server, and the
// checkout's state must not say it did.
func keepLastContact(checkout *delivery.Checkout, main delivery.State) error {
	lockCtx, cancel := delivery.LockContext(context.Background())
	defer cancel()
	unlock, err := checkout.Lock(lockCtx)
	if err != nil {
		return err
	}
	defer unlock()
	state, err := checkout.LoadState()
	if err != nil {
		return err
	}
	state.CheckedAt, state.ETag = main.CheckedAt, ""
	state.Latest = max(state.Latest, main.Latest)
	return checkout.SaveState(state)
}

// placed reports a sync's warnings and turns a release that is not whole in the checkout into
// an error.
func placed(out delivery.Outcome, stderr io.Writer) error {
	for _, warning := range out.Warnings {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", warning)
	}
	if len(out.Problems) == 0 {
		return nil
	}
	paths := make([]string, 0, len(out.Problems))
	for _, problem := range out.Problems {
		paths = append(paths, problem.Path+" ("+problem.Reason+")")
	}
	return fmt.Errorf("%d delivered paths of release %d are not in place: %s", len(out.Problems), out.Active, strings.Join(paths, ", "))
}

// mainWorktree is the first worktree git lists for the repository of root: the main worktree.
func mainWorktree(ctx context.Context, root string) (string, error) {
	cmd := exec.CommandContext(ctx, "git", "worktree", "list", "--porcelain", "-z")
	cmd.Dir = root
	var stdout, stderr bytes.Buffer
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	if err := cmd.Run(); err != nil {
		return "", fmt.Errorf("list the worktrees: %w: %s", err, strings.TrimSpace(stderr.String()))
	}
	for _, field := range strings.Split(stdout.String(), "\x00") {
		if path, ok := strings.CutPrefix(field, "worktree "); ok {
			return path, nil
		}
	}
	return "", nil
}

// sameDir reports whether a and b name the same directory, following links such as /tmp.
func sameDir(a, b string) bool {
	ra, errA := filepath.EvalSymlinks(a)
	rb, errB := filepath.EvalSymlinks(b)
	if errA != nil || errB != nil {
		return filepath.Clean(a) == filepath.Clean(b)
	}
	return ra == rb
}

// readHeldRelease reads the signed release number that the worktree at root built. It is checked
// only by the sync that takes it.
func readHeldRelease(root string, number int) (client.DeliveryRelease, error) {
	path := filepath.Join(root, ".kaya", "releases", fmt.Sprintf("%d.json", number))
	data, err := os.ReadFile(path) // #nosec G304 -- the release file below another worktree's .kaya of the same repository
	if err != nil {
		return client.DeliveryRelease{}, fmt.Errorf("read release %d: %w", number, err)
	}
	var release client.DeliveryRelease
	if err := json.Unmarshal(data, &release); err != nil {
		return client.DeliveryRelease{}, fmt.Errorf("%s is damaged: %w", path, err)
	}
	if release.Number != number || release.ReleaseID == "" {
		return client.DeliveryRelease{}, fmt.Errorf("%s does not hold release %d", path, number)
	}
	return release, nil
}

// heldSource serves one release another worktree of this laptop built, as the current release,
// so that a sync verifies and builds it as it would one from Kaya. It has no blob: every blob
// must already be in this laptop's cache.
type heldSource struct {
	release client.DeliveryRelease
	from    string
}

func (h heldSource) CurrentRelease(context.Context, string, string) (client.DeliveryCurrent, string, bool, error) {
	r := h.release
	return client.DeliveryCurrent{RepositoryID: r.RepositoryID, ReleaseID: r.ReleaseID, Number: r.Number, ManifestSHA256: r.ManifestSHA256}, "", false, nil
}

func (h heldSource) ReleaseByID(_ context.Context, releaseID string) (client.DeliveryRelease, error) {
	if releaseID != h.release.ReleaseID {
		return client.DeliveryRelease{}, fmt.Errorf("release %s is not held in %s", releaseID, h.from)
	}
	return h.release, nil
}

func (h heldSource) Blob(_ context.Context, sha256 string) ([]byte, error) {
	return nil, fmt.Errorf("blob %s is not in this laptop's cache, and Kaya was not reached", sha256)
}

var plainArg = regexp.MustCompile(`^[A-Za-z0-9_@%+=:,./-]+$`)

// shellQuote quotes a path for a POSIX shell when it needs it.
func shellQuote(s string) string {
	if plainArg.MatchString(s) {
		return s
	}
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}
