package delivery

import (
	"context"
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
)

const (
	// onlineTimeout bounds the API calls of a launch or a session start: past it the session
	// starts on the cached release.
	onlineTimeout = 3 * time.Second
	// lockTimeout bounds the wait for another oba kaya command of the same checkout.
	lockTimeout = 10 * time.Second
)

// Variables the launcher hands to Claude Code and its hooks.
const (
	EnvRelease      = "KAYA_RELEASE"
	EnvReleaseState = "KAYA_RELEASE_STATE"
	EnvLaunched     = "KAYA_LAUNCHED"
	EnvCheckout     = "KAYA_CHECKOUT"
	// EnvOffline carries the reason the launcher did not reach Kaya to the session start hook.
	EnvOffline = "KAYA_OFFLINE"
)

// Machine is how a command reaches Kaya: the API with the machine token, found once per command
// since each read may start kagi. API is nil without a token, and Err then says why.
type Machine struct {
	API *client.Client
	Err error
}

// ConnectMachine finds the machine token within onlineTimeout. Only an API that is reached over
// neither https nor loopback is an error: without a token, a command works from its caches and
// says why.
func ConnectMachine(ctx context.Context) (Machine, error) {
	apiURL := machine.APIURL()
	if err := machine.CheckTransport(apiURL); err != nil {
		return Machine{}, err
	}
	tokenCtx, cancel := context.WithTimeout(ctx, onlineTimeout)
	defer cancel()
	token, err := machine.Token(tokenCtx)
	if err != nil {
		return Machine{Err: err}, nil
	}
	return Machine{API: client.New(apiURL, "", func(context.Context) (string, error) { return token, nil })}, nil
}

// RefreshRepositoryList refreshes the cached repository list with the machine token; without a
// token the cache stays and the error says why.
func (m Machine) RefreshRepositoryList(ctx context.Context) (RepositoryList, error) {
	if m.API == nil {
		cached, loadErr := LoadRepositoryList()
		reason := m.Err
		if reason == nil {
			reason = errors.New("no machine token")
		}
		return cached, errors.Join(refreshFailed(reason), loadErr)
	}
	return RefreshRepositoryList(ctx, m.API)
}

// Open prepares delivery for a checkout. Online, it also finds the machine token; without one
// the deliverer works from the cache and says why.
func Open(ctx context.Context, checkout *Checkout, online bool) (*Deliverer, error) {
	if !online {
		return OpenWith(checkout, Machine{})
	}
	m, err := ConnectMachine(ctx)
	if err != nil {
		return nil, err
	}
	return OpenWith(checkout, m)
}

// OpenWith prepares delivery for a checkout with a machine found before; an empty Machine works
// offline.
func OpenWith(checkout *Checkout, m Machine) (*Deliverer, error) {
	keys, err := TrustedKeys(machine.APIURL())
	if err != nil {
		return nil, err
	}
	cache, err := DefaultCache()
	if err != nil {
		return nil, err
	}
	d := &Deliverer{Checkout: checkout, Keys: keys, Cache: cache, OnlineTimeout: onlineTimeout, SourceError: m.Err}
	// A nil *client.Client must not become a non-nil Source.
	if m.API != nil {
		d.Source = m.API
	}
	return d, nil
}

// LockContext bounds a command's wait for the checkout lock.
func LockContext(ctx context.Context) (context.Context, context.CancelFunc) {
	return context.WithTimeout(ctx, lockTimeout)
}

// CurrentCheckout is the checkout of the working directory.
func CurrentCheckout() (*Checkout, error) {
	dir, err := os.Getwd()
	if err != nil {
		return nil, fmt.Errorf("read the working directory: %w", err)
	}
	return FindCheckout(dir)
}

// NotDelivered is the answer of a delivery command in a checkout Kaya does not manage.
func NotDelivered(checkout *Checkout) error {
	switch checkout.Kind {
	case NotRepository:
		return fmt.Errorf("%s is in no git checkout", checkout.Root)
	case NotKaya:
		err := fmt.Errorf("no remote of %s is on the organization's repository list; its agent config is not delivered by Kaya", checkout.Root)
		for _, warning := range UnresolvedWarnings(checkout) {
			err = fmt.Errorf("%w; %s", err, warning)
		}
		return err
	case NotCutOver:
		return fmt.Errorf("%s still syncs from korurlabs/agents (.agents/.managed.json); Kaya delivers to it after the cutover", checkout.Root)
	}
	return errors.New("the checkout is delivered by Kaya")
}

// FeedbackSourceOf is the plan feedback source of d, nil when d has none.
func FeedbackSourceOf(d *Deliverer) FeedbackSource {
	if d == nil {
		return nil
	}
	src, _ := d.Source.(FeedbackSource)
	return src
}
