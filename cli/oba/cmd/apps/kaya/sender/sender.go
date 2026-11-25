// Package sender builds oba kaya sender, the background process that sends the recorded
// sessions to Kaya.
package sender

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/sender"
)

// senderTokenTimeout bounds the read of the machine token through kagi, which may have to reach
// Kagi first.
const senderTokenTimeout = 30 * time.Second

// localBuildVersion is the version a build reports when none was set at build time, as main
// defaults it.
const localBuildVersion = "dev"

var senderOnce bool

// senderRun is the send loop, which a test replaces to see the configuration the command passes.
var senderRun = sender.Run

// NewCmd builds the oba kaya sender command.
func NewCmd() *cobra.Command {
	senderOnce = false
	var senderCmd = &cobra.Command{
		Use:    "sender",
		Short:  "Send the queued sessions to Kaya (started by oba kaya hook record)",
		Long:   "Sends the session queue that oba kaya hook record writes to Kaya as the machine token (KAYA_RECORDER_TOKEN, else from the Kagi personal environment). One sender runs per machine; it logs to recorder.log in the state directory and exits after ten idle minutes. --once sends one round.",
		Hidden: true,
		Args:   cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, stop := signal.NotifyContext(cmd.Context(), syscall.SIGINT, syscall.SIGTERM)
			defer stop()
			err := senderRun(ctx, sender.Config{
				APIURL: machine.APIURL(),
				Token: func(ctx context.Context) (string, error) {
					tokenCtx, cancel := context.WithTimeout(ctx, senderTokenTimeout)
					defer cancel()
					return machine.Token(tokenCtx)
				},
				Once:    senderOnce,
				Version: runningVersion(cmd),
			})
			if err == nil {
				return nil
			}
			// A detached sender has no caller; its log is where the developer looks.
			if logErr := capture.Log("sender failed: " + err.Error()); logErr != nil {
				_, _ = fmt.Fprintf(os.Stderr, "oba kaya sender: %v: sender failed: %v\n", logErr, err)
			}
			return err
		},
	}

	senderCmd.Flags().BoolVar(&senderOnce, "once", false, "send one round and exit")
	return senderCmd
}

// runningVersion is the version of this oba, which the sender records in a park. Each other version
// sends a park after a machine name refusal once more, and a sender without a version never does,
// so a build whose version was set empty counts as a local build: it sends such a park once and
// waits after a second refusal, like any version.
func runningVersion(cmd *cobra.Command) string {
	if version := cmd.Root().Version; version != "" {
		return version
	}
	return localBuildVersion
}
