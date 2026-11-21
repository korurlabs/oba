package delivery

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

func newWatchCmd() *cobra.Command {
	var watchCheckout string
	cmd := &cobra.Command{
		Use:   "watch",
		Short: "Prepare the next release and deliver plan feedback while the checkout's sessions run",
		Long: "Asks Kaya every minute for the checkout's current release and prepares a newer one for the " +
			"next session, without switching. Every 15 seconds it fetches the reviewer feedback " +
			"on the live sessions' plans into .kaya/inbox/, where the session's next prompt or tool call picks " +
			"it up, and posts the feedback the hooks delivered. oba kaya launch starts it; it exits when the " +
			"checkout has no live session, and at once when a watcher of the checkout already runs.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			dir := watchCheckout
			if dir == "" {
				wd, err := os.Getwd()
				if err != nil {
					return fmt.Errorf("read the working directory: %w", err)
				}
				dir = wd
			}
			checkout, err := delivery.FindCheckout(dir)
			if err != nil {
				return err
			}
			if checkout.Kind != delivery.Delivered {
				return delivery.NotDelivered(checkout)
			}
			ctx, stop := signal.NotifyContext(cmd.Context(), syscall.SIGINT, syscall.SIGTERM)
			defer stop()
			return delivery.Watch(ctx, checkout, cmd.ErrOrStderr())
		},
	}
	cmd.Flags().StringVar(&watchCheckout, "checkout", "", "the checkout to watch (default: the working directory's)")
	return cmd
}
