package updates

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newMissingCmd() *cobra.Command {
	var day string
	cmd := &cobra.Command{
		Use:   "missing",
		Short: "List the members who have not posted their daily update",
		Long: `List the members of an organization who have not posted a daily update by
10:30 Europe/Istanbul time, then those who posted late. Only an OWNER or ADMIN
of the organization may run it; anyone else gets a permission error.

Expected are the organization's active OWNERs, ADMINs and MEMBERs, not its
GUESTs. Any update counts, in whichever organization it was posted.

The organization is --org, else the current organization ('oba org use'). Without
--date the day is today in Europe/Istanbul; a past day is judged by today's
memberships, and a future day is refused. STATUS is "missing" once the deadline
has passed, "pending" before it, and "late HH:MM" (Istanbul time) for a first
update posted after it. No update is expected on Saturday or Sunday.`,
		Example: `  oba kazan updates missing
  oba kazan updates missing --date 2026-10-16
  oba kazan updates missing --org Acme --json`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := updates.CheckDay(day); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			report, err := updates.Missing(c, day)
			if err != nil {
				return err
			}
			return updates.PrintMissing(cmd.OutOrStdout(), report)
		},
	}
	cmd.Flags().StringVar(&day, "date", "", "the Europe/Istanbul day (YYYY-MM-DD); default: today")
	return cmd
}
