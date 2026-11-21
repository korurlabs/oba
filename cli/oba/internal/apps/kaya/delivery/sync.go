package delivery

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// Release is a signed release with its verified manifest.
type Release struct {
	Signed   client.DeliveryRelease
	Manifest Manifest
}

// Source is the part of the Kaya API delivery reads.
type Source interface {
	CurrentRelease(ctx context.Context, remoteURL, etag string) (client.DeliveryCurrent, string, bool, error)
	ReleaseByID(ctx context.Context, releaseID string) (client.DeliveryRelease, error)
	Blob(ctx context.Context, sha256 string) ([]byte, error)
}

// Policy says when a sync may switch the checkout to a newer release.
type Policy int

const (
	// Pin switches only while no other session of the checkout is live; a session keeps the
	// release it started on.
	Pin Policy = iota
	// KeepAlwaysOn also keeps the active release when the newer one changes an always-on file,
	// for a session that has already loaded them.
	KeepAlwaysOn
	// Force switches even while other sessions are live.
	Force
)

// blobWorkers is how many blobs a sync downloads at once.
const blobWorkers = 4

// Deliverer syncs one checkout with Kaya.
type Deliverer struct {
	Checkout *Checkout
	// Source is nil without a machine token, which leaves the checkout on its cached releases.
	Source Source
	// SourceError is why Source is nil, for the session's message.
	SourceError error
	Keys        Keys
	Cache       Cache
	// OnlineTimeout bounds every API call of one sync together.
	OnlineTimeout time.Duration
	// ExcludePID is the caller's own Claude Code process, which never counts as another session.
	ExcludePID int
}

// Outcome is what a sync did and the release state the session reports.
type Outcome struct {
	State           string
	Active          int
	ActiveCreatedAt string
	ManifestSHA256  string
	Previous        int
	Latest          int
	Staged          int
	Switched        bool
	// AlwaysOnStaged: the staged release changes always-on files and needs a restart.
	AlwaysOnStaged bool
	// Offline is why Kaya was not reached, or why its newest release was not taken.
	Offline      error
	LiveSessions []Session
	Changed      []string
	Restored     []Problem
	Problems     []Problem
	Warnings     []string
}

// Sync brings the checkout to the newest verified release the policy allows, restores delivered
// paths changed by hand and computes the release state. A failure to reach Kaya or to take a
// newer release never touches the active release.
func (d *Deliverer) Sync(ctx context.Context, policy Policy) (Outcome, error) {
	c := d.Checkout
	unlock, err := c.Lock(ctx)
	if err != nil {
		return Outcome{}, err
	}
	defer unlock()

	state, err := c.LoadState()
	if err != nil {
		return Outcome{}, err
	}
	request, normalized, err := c.Remotes()
	if err != nil {
		return Outcome{}, err
	}
	var out Outcome
	organization := d.organization(state)
	active, activeErr := d.loadActive(state, organization)
	// cleanup is the release whose links a switch removes when the next one no longer delivers them.
	cleanup := active
	if state.RemoteURL != "" && state.RemoteURL != normalized {
		// Releases of the old remote are not this repository's; only the organization pin stays.
		out.Warnings = append(out.Warnings, fmt.Sprintf("the remote changed from %s to %s; the releases of the old remote are no longer used", state.RemoteURL, normalized))
		state = State{Organization: state.Organization}
		active, activeErr = nil, nil
	}
	state.RequestRemote, state.RemoteURL = request, normalized

	if d.Source == nil {
		out.Offline = d.SourceError
		if out.Offline == nil {
			out.Offline = errors.New("no machine token")
		}
	} else {
		d.contact(ctx, &state, &out, organization)
	}
	organization = d.organization(state)
	if activeErr != nil {
		out.Warnings = append(out.Warnings, activeErr.Error())
	}

	live, err := c.LiveSessions(d.ExcludePID)
	if err != nil {
		out.Warnings = append(out.Warnings, err.Error())
	}
	out.LiveSessions = live
	next := d.newestHeld(state, organization, active, &out)
	if next != nil {
		d.switchTo(ctx, policy, &state, &out, active, next, cleanup)
		if out.Switched {
			active = next
			activeErr = nil
		}
	}
	if active != nil {
		d.verifyPlaced(ctx, &out, active)
	}
	d.finish(&out, state, active, activeErr)
	if err := c.prune(state); err != nil {
		out.Warnings = append(out.Warnings, err.Error())
	}
	return out, c.SaveState(state)
}

// organization is the organization a release must be signed for: kaya.yaml's pin, else the one
// the first verified release pinned.
func (d *Deliverer) organization(state State) string {
	if d.Checkout.Config.Organization != "" {
		return d.Checkout.Config.Organization
	}
	return state.Organization
}

func (d *Deliverer) loadActive(state State, organization string) (*Release, error) {
	if state.Active == 0 {
		return nil, nil
	}
	return d.loadHeld(state.Active, organization, state.RemoteURL)
}

// loadHeld reads a built release and checks it again against the keys and the checkout.
func (d *Deliverer) loadHeld(number int, organization, remoteURL string) (*Release, error) {
	if !d.Checkout.hasVersion(number) {
		return nil, fmt.Errorf("release %d is not built in this checkout", number)
	}
	signed, manifest, err := d.Checkout.loadRelease(number, d.Keys)
	if err != nil {
		return nil, err
	}
	if err := CheckOrigin(manifest, organization, remoteURL, 0); err != nil {
		return nil, err
	}
	return &Release{Signed: signed, Manifest: manifest}, nil
}

// contact asks Kaya for the current release, and fetches and builds a newer release. Every failure is recorded in out and leaves the state of the last good contact.
func (d *Deliverer) contact(ctx context.Context, state *State, out *Outcome, organization string) {
	online, cancel := context.WithTimeout(ctx, d.OnlineTimeout)
	defer cancel()
	etag := state.ETag
	if state.Latest > 0 && !d.Checkout.hasVersion(state.Latest) {
		// The ETag stands for a release this checkout does not hold; ask again in full.
		etag = ""
	}
	current, newETag, notModified, err := d.Source.CurrentRelease(online, state.RequestRemote, etag)
	if err != nil {
		out.Offline = err
		return
	}
	state.CheckedAt = time.Now().UTC().Format(time.RFC3339)
	if notModified {
		return
	}
	state.ETag = newETag
	if current.ReleaseID != "" && current.Number > 0 {
		state.Latest = current.Number
		if !d.Checkout.hasVersion(current.Number) {
			release, err := d.fetch(online, current, organization, state)
			if err != nil {
				out.Offline = fmt.Errorf("release %d was not taken: %w", current.Number, err)
				// A release that was not taken drops the ETag, so the next contact asks again in full.
				state.ETag = ""
			} else if state.Organization == "" {
				state.Organization = release.Manifest.Organization
			}
		}
	} else {
		state.Latest = 0
	}
}

// fetch reads, verifies and builds a release. Nothing reaches .kaya/versions/ before the
// signature, the origin, the number and every path rule are checked and every blob hash matches.
func (d *Deliverer) fetch(ctx context.Context, current client.DeliveryCurrent, organization string, state *State) (*Release, error) {
	signed, err := d.Source.ReleaseByID(ctx, current.ReleaseID)
	if err != nil {
		return nil, err
	}
	if signed.Number != current.Number {
		return nil, fmt.Errorf("the server named release %d and served release %d", current.Number, signed.Number)
	}
	manifest, err := VerifyRelease(signed, d.Keys)
	if err != nil {
		return nil, err
	}
	if err := CheckOrigin(manifest, organization, state.RemoteURL, state.Highest); err != nil {
		return nil, err
	}
	if err := ValidateManifest(manifest); err != nil {
		return nil, err
	}
	if err := d.ensureBlobs(ctx, manifest); err != nil {
		return nil, err
	}
	if err := d.Checkout.buildVersion(signed, manifest, d.Cache); err != nil {
		return nil, err
	}
	state.Highest = max(state.Highest, manifest.Number)
	return &Release{Signed: signed, Manifest: manifest}, nil
}

// ensureBlobs downloads the blobs of a manifest that the cache lacks; each is stored only after
// its hash was checked.
func (d *Deliverer) ensureBlobs(ctx context.Context, manifest Manifest) error {
	seen := map[string]bool{}
	var missing []string
	for _, file := range manifest.Files {
		if file.Mode == ModeSymlink || seen[file.SHA256] || d.Cache.Has(file.SHA256) {
			continue
		}
		seen[file.SHA256] = true
		missing = append(missing, file.SHA256)
	}
	if len(missing) == 0 {
		return nil
	}
	if d.Source == nil {
		return fmt.Errorf("%d blobs of release %d are not cached and Kaya cannot be reached", len(missing), manifest.Number)
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	work := make(chan string)
	errs := make(chan error, len(missing))
	var wg sync.WaitGroup
	for range min(blobWorkers, len(missing)) {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for sha := range work {
				data, err := d.Source.Blob(ctx, sha)
				if err == nil {
					err = d.Cache.Put(sha, data)
				}
				if err != nil {
					errs <- fmt.Errorf("blob %s: %w", sha, err)
					cancel()
				}
			}
		}()
	}
feed:
	for _, sha := range missing {
		select {
		case work <- sha:
		case <-ctx.Done():
			break feed
		}
	}
	close(work)
	wg.Wait()
	close(errs)
	if err := <-errs; err != nil {
		return err
	}
	return ctx.Err()
}

// newestHeld is the newest built and verified release above the active one: the latest the
// server named, else a staged one.
func (d *Deliverer) newestHeld(state State, organization string, active *Release, out *Outcome) *Release {
	activeNumber := 0
	if active != nil {
		activeNumber = active.Manifest.Number
	}
	for _, number := range []int{state.Latest, state.Staged} {
		if number == 0 || number <= activeNumber || !d.Checkout.hasVersion(number) {
			continue
		}
		release, err := d.loadHeld(number, organization, state.RemoteURL)
		if err != nil {
			out.Warnings = append(out.Warnings, err.Error())
			continue
		}
		return release
	}
	return nil
}

// switchTo activates next unless the policy pins the active release, in which case next is
// staged for the end of the last live session.
func (d *Deliverer) switchTo(ctx context.Context, policy Policy, state *State, out *Outcome, active, next, cleanup *Release) {
	if active != nil {
		if policy != Force && len(out.LiveSessions) > 0 {
			state.Staged = next.Manifest.Number
			return
		}
		if policy == KeepAlwaysOn && AlwaysOnChanged(active.Manifest, next.Manifest) {
			state.Staged = next.Manifest.Number
			out.AlwaysOnStaged = true
			return
		}
	}
	if err := d.ensureBlobs(ctx, next.Manifest); err != nil {
		out.Warnings = append(out.Warnings, err.Error())
		return
	}
	var prev *Manifest
	if cleanup != nil {
		prev = &cleanup.Manifest
	}
	if err := d.Checkout.activate(next.Manifest, prev, next.Signed, d.Cache); err != nil {
		out.Warnings = append(out.Warnings, fmt.Sprintf("switch to release %d: %v", next.Manifest.Number, err))
		if active != nil {
			if err := d.Checkout.activate(active.Manifest, &next.Manifest, active.Signed, d.Cache); err != nil {
				out.Warnings = append(out.Warnings, fmt.Sprintf("return to release %d: %v", active.Manifest.Number, err))
			}
		}
		return
	}
	if active != nil {
		out.Previous = active.Manifest.Number
	}
	out.Switched = true
	state.Active = next.Manifest.Number
	state.Staged = 0
	if active != nil {
		out.Changed = Changes(active.Manifest, next.Manifest)
	}
}

// verifyPlaced restores every delivered path that differs from the active release, and records
// what could not be restored.
func (d *Deliverer) verifyPlaced(ctx context.Context, out *Outcome, active *Release) {
	problems := d.Checkout.Inspect(active.Manifest, d.Cache)
	if len(problems) == 0 {
		return
	}
	if err := d.ensureBlobs(ctx, active.Manifest); err != nil {
		out.Problems = problems
		out.Warnings = append(out.Warnings, err.Error())
		return
	}
	if err := d.Checkout.restore(problems, *active, d.Cache); err != nil {
		out.Warnings = append(out.Warnings, err.Error())
	}
	out.Problems = d.Checkout.Inspect(active.Manifest, d.Cache)
	if len(out.Problems) == 0 && !out.Switched {
		out.Restored = problems
	}
}

// finish fills the release state: NONE without a verified release, UNVERIFIED when the active
// one fails its check, STALE when a newer one waits, CACHED when Kaya was not reached or its
// newest release was not taken, FRESH otherwise.
func (d *Deliverer) finish(out *Outcome, state State, active *Release, activeErr error) {
	out.Latest = state.Latest
	out.Staged = state.Staged
	if active != nil {
		out.Active = active.Manifest.Number
		out.ActiveCreatedAt = active.Manifest.CreatedAt
		out.ManifestSHA256 = active.Signed.ManifestSHA256
	}
	switch {
	case active == nil && activeErr != nil:
		out.State = StateUnverified
	case active == nil:
		out.State = StateNone
	case len(out.Problems) > 0:
		out.State = StateUnverified
	case state.Staged > out.Active:
		out.State = StateStale
	case out.Offline != nil || state.Latest > out.Active:
		out.State = StateCached
	default:
		out.State = StateFresh
	}
}

// ActivateStaged switches to the staged release once no other session of the checkout is live.
// It reads only local state, for the end of a session.
func (d *Deliverer) ActivateStaged(ctx context.Context) (int, error) {
	c := d.Checkout
	unlock, err := c.Lock(ctx)
	if err != nil {
		return 0, err
	}
	defer unlock()
	state, err := c.LoadState()
	if err != nil || state.Staged == 0 || state.Staged <= state.Active {
		return 0, err
	}
	live, err := c.LiveSessions(d.ExcludePID)
	if err != nil || len(live) > 0 {
		return 0, err
	}
	organization := d.organization(state)
	next, err := d.loadHeld(state.Staged, organization, state.RemoteURL)
	if err != nil {
		return 0, err
	}
	var prev *Manifest
	if active, err := d.loadActive(state, organization); err == nil && active != nil {
		prev = &active.Manifest
	}
	if err := c.activate(next.Manifest, prev, next.Signed, d.Cache); err != nil {
		return 0, fmt.Errorf("switch to release %d: %w", next.Manifest.Number, err)
	}
	state.Active, state.Staged = next.Manifest.Number, 0
	return state.Active, c.SaveState(state)
}

// ActiveRelease reads and verifies the active release from local files only, for the checks of a
// tool call: no network and no hashing of the delivered tree.
func (d *Deliverer) ActiveRelease() (*Release, State, error) {
	state, err := d.Checkout.LoadState()
	if err != nil {
		return nil, state, err
	}
	active, err := d.loadActive(state, d.organization(state))
	return active, state, err
}

// Describe reports the checkout's release state from local files only. It changes nothing but
// the records of sessions whose process ended.
func (d *Deliverer) Describe() (Outcome, State, error) {
	state, err := d.Checkout.LoadState()
	if err != nil {
		return Outcome{}, state, err
	}
	var out Outcome
	active, activeErr := d.loadActive(state, d.organization(state))
	if activeErr != nil {
		out.Warnings = append(out.Warnings, activeErr.Error())
	}
	if active != nil {
		out.Problems = d.Checkout.Inspect(active.Manifest, d.Cache)
	}
	live, err := d.Checkout.LiveSessions(d.ExcludePID)
	if err != nil {
		out.Warnings = append(out.Warnings, err.Error())
	}
	out.LiveSessions = live
	d.finish(&out, state, active, activeErr)
	return out, state, nil
}

// ChangesSince lists the paths active changes compared with release last, when last is still
// held here; nil when it is not or when nothing changed.
func (d *Deliverer) ChangesSince(last int, active *Release, state State) []string {
	if active == nil || last == 0 || last == active.Manifest.Number {
		return nil
	}
	previous, err := d.loadHeld(last, d.organization(state), state.RemoteURL)
	if err != nil {
		return nil
	}
	return Changes(previous.Manifest, active.Manifest)
}

// RecordSessionStart remembers the release a session started on, so the next session start
// names the paths changed since.
func (d *Deliverer) RecordSessionStart(ctx context.Context, release int) error {
	unlock, err := d.Checkout.Lock(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	state, err := d.Checkout.LoadState()
	if err != nil {
		return err
	}
	state.LastSessionRelease = release
	return d.Checkout.SaveState(state)
}
