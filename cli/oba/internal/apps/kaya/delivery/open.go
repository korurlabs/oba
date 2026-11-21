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

// Open prepares delivery for a checkout. Online, it also finds the machine token; without one
// the deliverer works from the cache and says why.
func Open(ctx context.Context, checkout *Checkout, online bool) (*Deliverer, error) {
	apiURL := machine.APIURL()
	keys, err := TrustedKeys(apiURL)
	if err != nil {
		return nil, err
	}
	cache, err := DefaultCache()
	if err != nil {
		return nil, err
	}
	d := &Deliverer{Checkout: checkout, Keys: keys, Cache: cache, OnlineTimeout: onlineTimeout}
	if !online {
		return d, nil
	}
	if err := machine.CheckTransport(apiURL); err != nil {
		return nil, err
	}
	tokenCtx, cancel := context.WithTimeout(ctx, onlineTimeout)
	defer cancel()
	token, err := machine.Token(tokenCtx)
	if err != nil {
		d.SourceError = err
		return d, nil
	}
	d.Source = client.New(apiURL, "", func(context.Context) (string, error) { return token, nil })
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
		return fmt.Errorf("%s has no %s; its agent config is not delivered by Kaya", checkout.Root, ConfigFile)
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
