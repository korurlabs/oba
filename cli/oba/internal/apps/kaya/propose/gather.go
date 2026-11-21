package propose

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
)

// sendTimeout bounds the machine token lookup and the proposal call together.
const sendTimeout = 60 * time.Second

// Gather reads the changes of a checkout under its lock, so that no sync replaces a
// delivered path while it is read: the paths args name, else every change, read from the
// directory from when it is given, and the delivered paths removes names for removal.
func Gather(ctx context.Context, checkout *delivery.Checkout, d *delivery.Deliverer, args, removes []string, from string) (Source, delivery.State, []Change, error) {
	lockCtx, cancel := delivery.LockContext(ctx)
	defer cancel()
	unlock, err := checkout.Lock(lockCtx)
	if err != nil {
		return Source{}, delivery.State{}, nil, err
	}
	defer unlock()
	active, state, err := d.ActiveRelease()
	if err != nil {
		return Source{}, state, nil, fmt.Errorf("the active config release of %s does not verify (%w); run `oba kaya sync` online and propose again", checkout.Name(state), err)
	}
	if active == nil {
		return Source{}, state, nil, fmt.Errorf("no config release is active in %s; a proposal is made on the release a checkout runs, so run `oba kaya sync` online first", checkout.Name(state))
	}
	src := Source{Checkout: checkout, Release: active.Manifest, Cache: d.Cache}
	cwd, err := os.Getwd()
	if err != nil {
		return src, state, nil, fmt.Errorf("read the working directory: %w", err)
	}
	removals, err := ResolveArgs(src, cwd, removes)
	if err != nil {
		return src, state, nil, err
	}
	if from != "" {
		abs, err := filepath.Abs(from)
		if err != nil {
			return src, state, nil, fmt.Errorf("resolve %s: %w", from, err)
		}
		if info, err := os.Stat(abs); err != nil || !info.IsDir() {
			return src, state, nil, fmt.Errorf("--from %s is not a directory", from)
		}
		src.From = abs
	}
	paths, err := ResolveArgs(src, cwd, args)
	if err != nil {
		return src, state, nil, err
	}
	changes, err := Collect(src, paths, removals)
	return src, state, changes, err
}

// Send sends the proposal with the machine token.
func Send(ctx context.Context, request client.ProposalRequest) (client.Proposal, error) {
	callCtx, cancel := context.WithTimeout(ctx, sendTimeout)
	defer cancel()
	api, err := machine.Client(callCtx, sendTimeout)
	if err != nil {
		return client.Proposal{}, err
	}
	return api.Propose(callCtx, request)
}
