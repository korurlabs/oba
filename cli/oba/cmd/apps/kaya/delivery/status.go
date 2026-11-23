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
		Short: "Show the checkout's kind and config release, from local files only",
		Long: "Shows whether Kaya delivers the checkout and which remote on the organization's repository list " +
			"names it; for a delivered checkout also the active release, its state, a staged release, the live " +
			"sessions of the checkout and delivered files that differ from the release. It calls no API and reads " +
			"the repository list as oba kaya sync last fetched it.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			checkout, err := delivery.CurrentCheckout()
			if err != nil {
				return err
			}
			if checkout.Kind == delivery.NotRepository {
				return delivery.NotDelivered(checkout)
			}
			w := cmd.OutOrStdout()
			if checkout.Kind != delivery.Delivered {
				if jsonout.Enabled() {
					return jsonresult.SetValue(delivery.NewRecognitionView(checkout))
				}
				_, _ = fmt.Fprintln(w, delivery.Recognition(checkout))
				for _, warning := range delivery.UnresolvedWarnings(checkout) {
					_, _ = fmt.Fprintln(w, "Warning: "+warning)
				}
				return nil
			}
			d, err := delivery.Open(cmd.Context(), checkout, false)
			if err != nil {
				return err
			}
			out, state, err := d.Describe()
			if err != nil {
				return err
			}
			if jsonout.Enabled() {
				return jsonresult.SetValue(delivery.NewOutcomeView(checkout, out, state))
			}
			_, _ = fmt.Fprintln(w, delivery.Recognition(checkout))
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
