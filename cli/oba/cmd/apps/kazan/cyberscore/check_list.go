package cyberscore

import (
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newCheckListCmd() *cobra.Command {
	var status string
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the checks of the organization",
		Long:  "Lists the checks by category, sort order and name.\n\n  oba kazan cyberscore check list --status in-progress",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if strings.TrimSpace(status) != "" {
				if _, err := cyberscore.ParseStatus(status); err != nil {
					return err
				}
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			checks, err := cyberscore.ListChecks(c, status)
			if err != nil {
				return err
			}
			return cyberscore.PrintChecks(cmd.OutOrStdout(), checks)
		},
	}
	cmd.Flags().StringVar(&status, "status", "", "filter by status: "+strings.Join(cyberscore.Statuses, ", "))
	return cmd
}
