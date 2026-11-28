package campaign

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const listHelp = `Lists every campaign of the current organization in one table, last changed first: NAME, START,
END, CONTENTS (the number of its contents), OWNER, ID.

--search keeps the campaigns whose name contains the text, case ignored; --event keeps the
campaigns of one event, by its id.

  oba otak campaign list --search wacht
  oba otak campaign list --event <eventId>`

func newListCmd() *cobra.Command {
	var query campaign.ListQuery
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the campaigns of the organization",
		Long:  listHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runList(cmd, query)
		},
	}
	cmd.Flags().StringVar(&query.Search, "search", "", "list only campaigns whose name contains this text, case ignored")
	cmd.Flags().StringVar(&query.EventID, "event", "", "list only the campaigns of this event id")
	return cmd
}

func runList(cmd *cobra.Command, query campaign.ListQuery) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	campaigns, err := campaign.List(c, query)
	if err != nil {
		return err
	}
	return campaign.PrintList(cmd.OutOrStdout(), campaigns)
}
