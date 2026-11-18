package cmd

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

var statusJSON bool

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show the checkout's config release, from local files only",
	Long: "Shows the active release, its state, a staged release, the live sessions of the checkout and " +
		"delivered files that differ from the release. It calls no API; kaya sync contacts Kaya.",
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
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
		out, state, err := d.Describe()
		if err != nil {
			return err
		}
		w := cmd.OutOrStdout()
		if statusJSON {
			return writeJSON(w, outcomeJSON(checkout, out, state))
		}
		printOutcome(w, "", checkout, out, state)
		if state.CheckedAt != "" {
			_, _ = fmt.Fprintln(w, "Last contact with Kaya: "+state.CheckedAt)
		}
		for _, session := range out.LiveSessions {
			_, _ = fmt.Fprintf(w, "Live session %s (pid %d) on release %d.\n", sessionName(session), session.PID, session.Release)
		}
		return nil
	},
}

func init() {
	statusCmd.Flags().BoolVar(&statusJSON, "json", false, "print the status as JSON")
	rootCmd.AddCommand(statusCmd)
}
