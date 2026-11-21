package delivery

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

func newStatusCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "status",
		Short: "Show the checkout's config release, from local files only",
		Long: "Shows the active release, its state, a staged release, the live sessions of the checkout and " +
			"delivered files that differ from the release. It calls no API; oba kaya sync contacts Kaya.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			checkout, err := delivery.CurrentCheckout()
			if err != nil {
				return err
			}
			if checkout.Kind != delivery.Delivered {
				return delivery.NotDelivered(checkout)
			}
			d, err := delivery.Open(cmd.Context(), checkout, false)
			if err != nil {
				return err
			}
			out, state, err := d.Describe()
			if err != nil {
				return err
			}
			w := cmd.OutOrStdout()
			if jsonout.Enabled() {
				return jsonresult.SetValue(delivery.NewOutcomeView(checkout, out, state))
			}
			delivery.PrintOutcome(w, "", checkout, out, state)
			if state.CheckedAt != "" {
				_, _ = fmt.Fprintln(w, "Last contact with Kaya: "+state.CheckedAt)
			}
			for _, session := range out.LiveSessions {
				_, _ = fmt.Fprintf(w, "Live session %s (pid %d) on release %d.\n", delivery.SessionName(session), session.PID, session.Release)
			}
			return nil
		},
	}
}
