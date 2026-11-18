package cmd

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/capture"
	"github.com/korurlabs/kaya-cli/internal/sender"
)

// senderTokenTimeout bounds the read of the machine token through kagi, which may have to reach
// Kagi first.
const senderTokenTimeout = 30 * time.Second

var senderOnce bool

var senderCmd = &cobra.Command{
	Use:    "sender",
	Short:  "Send the queued sessions to Kaya (started by kaya hook record)",
	Long:   "Sends the session queue that kaya hook record writes to Kaya as the machine token (KAYA_RECORDER_TOKEN, else from the Kagi personal environment). One sender runs per machine; it logs to recorder.log in the state directory and exits after ten idle minutes. --once sends one round.",
	Hidden: true,
	Args:   cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		ctx, stop := signal.NotifyContext(cmd.Context(), syscall.SIGINT, syscall.SIGTERM)
		defer stop()
		err := sender.Run(ctx, sender.Config{
			APIURL: deliveryAPIURL(),
			Token: func(ctx context.Context) (string, error) {
				tokenCtx, cancel := context.WithTimeout(ctx, senderTokenTimeout)
				defer cancel()
				return machineToken(tokenCtx)
			},
			Once: senderOnce,
		})
		if err == nil {
			return nil
		}
		// A detached sender has no caller; its log is where the developer looks.
		if logErr := capture.Log("sender failed: " + err.Error()); logErr != nil {
			_, _ = fmt.Fprintf(os.Stderr, "kaya sender: %v: sender failed: %v\n", logErr, err)
		}
		return err
	},
}

func init() {
	senderCmd.Flags().BoolVar(&senderOnce, "once", false, "send one round and exit")
	rootCmd.AddCommand(senderCmd)
}
