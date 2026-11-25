package cyberscore

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newTrendCmd() *cobra.Command {
	var days int
	cmd := &cobra.Command{
		Use:   "trend",
		Short: "Show the cyberscore of each of the last days",
		Long: "Shows one score per day, oldest first: the last score of each day, today's live score last. " +
			"Without --days the API takes the last 30 days.\n\n  oba kazan cyberscore trend --days 90",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if cmd.Flags().Changed("days") {
				if err := cyberscore.CheckDays(days); err != nil {
					return err
				}
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			points, err := cyberscore.Trend(c, days)
			if err != nil {
				return err
			}
			return cyberscore.PrintTrend(cmd.OutOrStdout(), points)
		},
	}
	cmd.Flags().IntVar(&days, "days", 0, "number of days, 1 to 365 (default: the API's 30)")
	return cmd
}
