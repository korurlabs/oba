package delivery

import (
	"context"
	"errors"
	"fmt"
	"io"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

// manualTimeout bounds the API calls of a sync typed by hand, which waits longer for Kaya than a
// session start does.
const manualTimeout = 60 * time.Second

func newSyncCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "sync",
		Short: "Bring the checkout to its newest signed release",
		Long: "Refreshes the cached list of the organization's repositories, by which a checkout is recognised " +
			"from its git remotes, then fetches and verifies the newest release of the checkout's repository, downloads only the files " +
			"the cache lacks, and switches to it unless another session of the checkout runs; then it restores " +
			"delivered files changed by hand. Exits 1 when the checkout has no verified release.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runSync(cmd.Context(), cmd.OutOrStdout(), delivery.Pin)
		},
	}
}

func newRefreshCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "refresh",
		Short: "Switch the checkout to its newest release even while sessions run",
		Long: "Like oba kaya sync, but switches while other sessions of the checkout run, and names them: their " +
			"skills change under them, their always-on rules only after a restart.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runSync(cmd.Context(), cmd.OutOrStdout(), delivery.Force)
		},
	}
}

func runSync(ctx context.Context, w io.Writer, policy delivery.Policy) error {
	m, err := delivery.ConnectMachine(ctx)
	if err != nil {
		return err
	}
	// The list is refreshed before the checkout is recognised, so a repository added to Kaya is
	// synced by its first oba kaya sync.
	refreshCtx, cancelRefresh := context.WithTimeout(ctx, manualTimeout)
	_, refreshErr := m.RefreshRepositoryList(refreshCtx)
	cancelRefresh()
	checkout, err := delivery.CurrentCheckout()
	if err != nil {
		return errors.Join(err, refreshErr)
	}
	if checkout.Kind != delivery.Delivered {
		return errors.Join(delivery.NotDelivered(checkout), refreshErr)
	}
	d, err := delivery.OpenWith(checkout, m)
	if err != nil {
		return err
	}
	d.OnlineTimeout = manualTimeout
	lockCtx, cancel := delivery.LockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, policy)
	if err != nil {
		return errors.Join(err, refreshErr)
	}
	if refreshErr != nil {
		out.Warnings = append(out.Warnings, refreshErr.Error())
	}
	state, err := checkout.LoadState()
	if err != nil {
		return err
	}
	if jsonout.Enabled() {
		if err := jsonresult.SetValue(delivery.NewOutcomeView(checkout, out, state)); err != nil {
			return err
		}
	} else {
		delivery.PrintOutcome(w, "", checkout, out, state)
		if policy == delivery.Force && out.Switched {
			for _, session := range out.LiveSessions {
				_, _ = fmt.Fprintf(w, "Switched under the running session %s (pid %d, release %d).\n", delivery.SessionName(session), session.PID, session.Release)
			}
		}
	}
	if out.State == delivery.StateNone || out.State == delivery.StateUnverified {
		// The state is the answer a script reads, so --json still prints it.
		jsonout.KeepOnFailure()
		return &exitcode.Error{Code: 1}
	}
	return nil
}
