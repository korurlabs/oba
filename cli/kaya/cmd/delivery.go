package cmd

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/korurlabs/kaya-cli/internal/auth"
	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/config"
	"github.com/korurlabs/kaya-cli/internal/delivery"
)

const (
	// onlineTimeout bounds the API calls of a launch or a session start: past it the session
	// starts on the cached release.
	onlineTimeout = 3 * time.Second
	// lockTimeout bounds the wait for another kaya command of the same checkout.
	lockTimeout = 10 * time.Second
	// envRecorderToken holds the machine token, the one the recorder's sender uses.
	envRecorderToken = "KAYA_RECORDER_TOKEN"
	// kagiRecorderPath is the Kagi app whose personal environment holds the machine token.
	kagiRecorderPath = "/kaya/recorder"
)

// Variables the launcher hands to Claude Code and its hooks.
const (
	envRelease      = "KAYA_RELEASE"
	envReleaseState = "KAYA_RELEASE_STATE"
	envLaunched     = "KAYA_LAUNCHED"
	envCheckout     = "KAYA_CHECKOUT"
)

// deliveryAPIURL is the API delivery calls: the command line, the environment, the URL of a
// stored sign-in, then production.
func deliveryAPIURL() string {
	var stored config.Config
	// rule-10-no-op -- reason: delivery needs no sign-in; without a readable one the command line, the environment and the production URL still apply, and kaya login reports a damaged sign-in
	if store, err := auth.DefaultStore(); err == nil {
		if creds, err := store.Load(); err == nil {
			stored.APIURL = creds.APIURL
		}
	}
	return config.Resolve(overrides, stored).APIURL
}

// machineToken is the machine token: KAYA_RECORDER_TOKEN, else from the developer's Kagi personal
// environment, as the recorder reads it.
func machineToken(ctx context.Context) (string, error) {
	if token := strings.TrimSpace(os.Getenv(envRecorderToken)); token != "" {
		return token, nil
	}
	kagi, err := exec.LookPath("kagi")
	if err != nil {
		return "", fmt.Errorf("no %s and no kagi on PATH", envRecorderToken)
	}
	cmd := exec.CommandContext(ctx, kagi, "run", "--personal", "--path", kagiRecorderPath, "--", // #nosec G204 -- fixed kagi arguments
		"sh", "-c", `printf %s "$`+envRecorderToken+`"`)
	out, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("read %s through kagi: %w", envRecorderToken, err)
	}
	token := strings.TrimSpace(string(out))
	if token == "" {
		return "", fmt.Errorf("no %s in your Kagi personal environment (kagi secrets set --personal --path %s %s=<token>)", envRecorderToken, kagiRecorderPath, envRecorderToken)
	}
	return token, nil
}

// newDeliverer prepares delivery for a checkout. Online, it also finds the machine token; without
// one the deliverer works from the cache and says why.
func newDeliverer(ctx context.Context, checkout *delivery.Checkout, online bool) (*delivery.Deliverer, error) {
	apiURL := deliveryAPIURL()
	keys, err := delivery.TrustedKeys(apiURL)
	if err != nil {
		return nil, err
	}
	cache, err := delivery.DefaultCache()
	if err != nil {
		return nil, err
	}
	d := &delivery.Deliverer{Checkout: checkout, Keys: keys, Cache: cache, OnlineTimeout: onlineTimeout}
	if !online {
		return d, nil
	}
	if err := delivery.CheckTransport(apiURL); err != nil {
		return nil, err
	}
	tokenCtx, cancel := context.WithTimeout(ctx, onlineTimeout)
	defer cancel()
	token, err := machineToken(tokenCtx)
	if err != nil {
		d.SourceError = err
		return d, nil
	}
	d.Source = client.New(apiURL, "", func(context.Context) (string, error) { return token, nil })
	return d, nil
}

// lockContext bounds a command's wait for the checkout lock.
func lockContext(ctx context.Context) (context.Context, context.CancelFunc) {
	return context.WithTimeout(ctx, lockTimeout)
}

// summary is the one-line state of a checkout's release, as commands print it.
func summary(checkout *delivery.Checkout, out delivery.Outcome, state delivery.State) string {
	name := checkout.Name(state)
	switch out.State {
	case delivery.StateNone:
		return fmt.Sprintf("No Kaya config on this machine for %s. Run `kaya sync` online once.", name)
	case delivery.StateUnverified:
		return fmt.Sprintf("The Kaya config of %s does not verify; tools are refused until `kaya sync` restores it.", name)
	}
	line := fmt.Sprintf("%s: config release %d (%s)", name, out.Active, out.State)
	if out.ActiveCreatedAt != "" {
		line += " of " + out.ActiveCreatedAt
	}
	return line
}

// details are the lines that explain a state beyond the summary.
func details(out delivery.Outcome) []string {
	var lines []string
	if out.Switched && out.Previous > 0 {
		lines = append(lines, fmt.Sprintf("Switched from release %d.", out.Previous))
	}
	if out.Staged > out.Active {
		switch {
		case out.AlwaysOnStaged:
			lines = append(lines, fmt.Sprintf("Config release %d changes always-on rules; restart Claude Code to use it.", out.Staged))
		case len(out.LiveSessions) > 0:
			lines = append(lines, fmt.Sprintf("Config release %d waits until the %d other sessions of this checkout end (or run `kaya refresh`).", out.Staged, len(out.LiveSessions)))
		default:
			lines = append(lines, fmt.Sprintf("Config release %d is staged for the next session.", out.Staged))
		}
	}
	if out.Offline != nil && out.Active > 0 {
		lines = append(lines, fmt.Sprintf("Kaya is unreachable or its newest release was not taken (%v); this session runs on config release %d of %s.", out.Offline, out.Active, out.ActiveCreatedAt))
	} else if out.Offline != nil {
		lines = append(lines, fmt.Sprintf("Kaya was not reached: %v", out.Offline))
	}
	for _, problem := range out.Restored {
		lines = append(lines, "Restored "+problem.String())
	}
	for _, problem := range out.Problems {
		lines = append(lines, "Not restored: "+problem.String())
	}
	for _, warning := range out.Warnings {
		lines = append(lines, "Warning: "+warning)
	}
	return lines
}

func printOutcome(w io.Writer, prefix string, checkout *delivery.Checkout, out delivery.Outcome, state delivery.State) {
	_, _ = fmt.Fprintln(w, prefix+summary(checkout, out, state))
	for _, line := range details(out) {
		_, _ = fmt.Fprintln(w, prefix+line)
	}
}

// errNotDelivered is the answer of a delivery command in a checkout Kaya does not manage.
func errNotDelivered(checkout *delivery.Checkout) error {
	switch checkout.Kind {
	case delivery.NotRepository:
		return fmt.Errorf("%s is in no git checkout", checkout.Root)
	case delivery.NotKaya:
		return fmt.Errorf("%s has no %s; its agent config is not delivered by Kaya", checkout.Root, delivery.ConfigFile)
	case delivery.NotCutOver:
		return fmt.Errorf("%s still syncs from korurlabs/agents (.agents/.managed.json); Kaya delivers to it after the cutover", checkout.Root)
	}
	return errors.New("the checkout is delivered by Kaya")
}

// currentCheckout is the checkout of the working directory.
func currentCheckout() (*delivery.Checkout, error) {
	dir, err := os.Getwd()
	if err != nil {
		return nil, fmt.Errorf("read the working directory: %w", err)
	}
	return delivery.FindCheckout(dir)
}
