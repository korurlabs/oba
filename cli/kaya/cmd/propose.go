package cmd

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/delivery"
	"github.com/korurlabs/kaya-cli/internal/propose"
)

// proposeTimeout bounds the token lookup and the proposal call; the server validates and
// stores the whole change in one transaction.
const proposeTimeout = 60 * time.Second

type proposeOptions struct {
	reason string
	from   string
	remove []string
	dryRun bool
}

var proposeOpts proposeOptions

var proposeCmd = &cobra.Command{
	Use:   "propose [--reason <text>] [--from <dir>] [--remove <path>...] [<path>...]",
	Short: "Propose a change of the delivered config for review in Kaya",
	Long: `Collects the config files changed against the release this checkout runs and proposes them
in Kaya as one change, in review at once. A path is a delivered file you edited, replaced or
deleted, or a new file at a path Kaya delivers; a directory stands for every config file below it.
Without paths it proposes every changed delivered path and every new config file.

A session agent may not edit delivered paths in place. It writes the new files below a scratch
directory with the repository's layout and passes it as --from; a path named then is a path of
the repository, read from that directory. --remove names a delivered path to remove.

The CLI refuses locally what Kaya would refuse: paths off the allow-list, links at a SKILL.md, a
hook or the settings, links that leave the repository or end at nothing, paths that differ only
in letter case, and files that break a size limit unless the release already held them over it
and they do not grow. The deny rules the release's guardrails merge into the settings are never
proposed. Without --reason it asks for one (10 to 1000 characters) when run in a terminal.`,
	RunE: runPropose,
}

func init() {
	flags := proposeCmd.Flags()
	flags.StringVar(&proposeOpts.reason, "reason", "", "why the change is needed, 10 to 1000 characters; the reviewer reads it")
	flags.StringVar(&proposeOpts.from, "from", "", "directory with the repository's layout to read the named paths from, instead of the checkout")
	flags.StringArrayVar(&proposeOpts.remove, "remove", nil, "delivered path to remove (repeatable)")
	flags.BoolVar(&proposeOpts.dryRun, "dry-run", false, "list and check the changes without sending them")
	rootCmd.AddCommand(proposeCmd)
}

func runPropose(cmd *cobra.Command, args []string) error {
	checkout, err := currentCheckout()
	if err != nil {
		return err
	}
	if checkout.Kind != delivery.Delivered {
		return errNotDelivered(checkout)
	}
	d, err := newDeliverer(cmd.Context(), checkout, false)
	if err != nil {
		return err
	}
	src, state, changes, err := collectChanges(cmd.Context(), checkout, d, args)
	if err != nil {
		return err
	}
	release := src.Release.Number
	w := cmd.OutOrStdout()
	if len(changes) == 0 {
		return fmt.Errorf("nothing changed against config release %d of %s", release, checkout.Name(state))
	}
	_, _ = fmt.Fprintf(w, "%d changes against config release %d of %s:\n", len(changes), release, checkout.Name(state))
	for _, line := range propose.Describe(changes) {
		_, _ = fmt.Fprintln(w, "  "+line)
	}
	if err := propose.Check(src, changes); err != nil {
		return err
	}
	if proposeOpts.dryRun {
		_, _ = fmt.Fprintln(w, "Dry run: Kaya would accept these paths; nothing was sent.")
		return nil
	}

	reason, err := proposalReason(cmd.InOrStdin(), w)
	if err != nil {
		return err
	}
	remote := state.RequestRemote
	if remote == "" {
		if remote, _, err = checkout.Remotes(); err != nil {
			return err
		}
	}
	request, err := propose.Request(remote, release, reason, changes)
	if err != nil {
		return err
	}
	proposal, err := sendProposal(cmd.Context(), request)
	if err != nil {
		return propose.Explain(err, release)
	}
	printProposal(w, proposal, release)
	return nil
}

// collectChanges reads the changes under the checkout lock, so that no sync replaces a
// delivered path while it is read.
func collectChanges(ctx context.Context, checkout *delivery.Checkout, d *delivery.Deliverer, args []string) (propose.Source, delivery.State, []propose.Change, error) {
	lockCtx, cancel := lockContext(ctx)
	defer cancel()
	unlock, err := checkout.Lock(lockCtx)
	if err != nil {
		return propose.Source{}, delivery.State{}, nil, err
	}
	defer unlock()
	active, state, err := d.ActiveRelease()
	if err != nil {
		return propose.Source{}, state, nil, fmt.Errorf("the active config release of %s does not verify (%w); run `kaya sync` online and propose again", checkout.Name(state), err)
	}
	if active == nil {
		return propose.Source{}, state, nil, fmt.Errorf("no config release is active in %s; a proposal is made on the release a checkout runs, so run `kaya sync` online first", checkout.Name(state))
	}
	src := propose.Source{Checkout: checkout, Release: active.Manifest, Cache: d.Cache}
	cwd, err := os.Getwd()
	if err != nil {
		return src, state, nil, fmt.Errorf("read the working directory: %w", err)
	}
	removals, err := propose.ResolveArgs(src, cwd, proposeOpts.remove)
	if err != nil {
		return src, state, nil, err
	}
	if proposeOpts.from != "" {
		from, err := filepath.Abs(proposeOpts.from)
		if err != nil {
			return src, state, nil, fmt.Errorf("resolve %s: %w", proposeOpts.from, err)
		}
		if info, err := os.Stat(from); err != nil || !info.IsDir() {
			return src, state, nil, fmt.Errorf("--from %s is not a directory", proposeOpts.from)
		}
		src.From = from
	}
	paths, err := propose.ResolveArgs(src, cwd, args)
	if err != nil {
		return src, state, nil, err
	}
	changes, err := propose.Collect(src, paths, removals)
	return src, state, changes, err
}

// proposalReason is --reason, else a line read from a terminal.
func proposalReason(in io.Reader, w io.Writer) (string, error) {
	reason := strings.TrimSpace(proposeOpts.reason)
	if reason == "" {
		if !interactive(in) {
			return "", errors.New("pass --reason <text>: why the change is needed, 10 to 1000 characters")
		}
		_, _ = fmt.Fprint(w, "Why is this change needed? (10 to 1000 characters, for the reviewer): ")
		line, err := bufio.NewReader(in).ReadString('\n')
		if err != nil && !errors.Is(err, io.EOF) {
			return "", fmt.Errorf("read the reason: %w", err)
		}
		reason = strings.TrimSpace(line)
	}
	return reason, propose.CheckReason(reason)
}

// interactive reports whether the reason can be asked for: stdin is a terminal, or a reader a
// test hands in.
func interactive(in io.Reader) bool {
	file, ok := in.(*os.File)
	if !ok {
		return true
	}
	info, err := file.Stat()
	return err == nil && info.Mode()&os.ModeCharDevice != 0
}

func sendProposal(ctx context.Context, request client.ProposalRequest) (client.Proposal, error) {
	apiURL := deliveryAPIURL()
	if err := delivery.CheckTransport(apiURL); err != nil {
		return client.Proposal{}, err
	}
	callCtx, cancel := context.WithTimeout(ctx, proposeTimeout)
	defer cancel()
	token, err := machineToken(callCtx)
	if err != nil {
		return client.Proposal{}, err
	}
	api := client.New(apiURL, "", func(context.Context) (string, error) { return token, nil })
	return api.Propose(callCtx, request)
}

func printProposal(w io.Writer, proposal client.Proposal, release int) {
	_, _ = fmt.Fprintf(w, "Proposal %d of %s is in review: %s\n", proposal.Number, proposal.Name, proposal.ReviewURL)
	if proposal.ExecutableChange {
		_, _ = fmt.Fprintln(w, "It changes an executable file, so an OWNER other than you approves it.")
	} else {
		_, _ = fmt.Fprintln(w, "An ADMIN or OWNER other than you approves it.")
	}
	_, _ = fmt.Fprintf(w, "Until a release carries it, this checkout runs config release %d, and `kaya sync` puts the delivered files back.\n", release)
}
