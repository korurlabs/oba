package campaign

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const listHelp = `Lists every campaign of the current organization in one table: NAME, CHANNEL, STATUS, PLANNED,
PUBLISHED, LANGUAGES, OWNER, ID. PLANNED and PUBLISHED are shown in this machine's time zone;
LANGUAGES are the languages that have a text.

--status keeps one status (draft, scheduled, published, archived), --channel one channel
(linkedin, x, newsletter), both case ignored; --search keeps the campaigns whose name contains
the text, case ignored. With --status scheduled the list runs soonest planned first, otherwise
last changed first.

  oba otak campaign list --status scheduled
  oba otak campaign list --channel linkedin --search launch`

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
	cmd.Flags().StringVar(&query.Status, "status", "", "list only campaigns with this status: draft, scheduled, published or archived")
	cmd.Flags().StringVar(&query.Channel, "channel", "", "list only campaigns on this channel: linkedin, x or newsletter")
	cmd.Flags().StringVar(&query.Search, "search", "", "list only campaigns whose name contains this text, case ignored")
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
