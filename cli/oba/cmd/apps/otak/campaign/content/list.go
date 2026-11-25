package content

import (
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign/content"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const listHelp = `Lists every content of one campaign, soonest planned first, unplanned last, then last changed
first: STATUS, CHANNEL, LANGUAGE, LEGAL ENTITY (its short name), PLANNED, PUBLISHED, TEXT (its
first 40 characters), ID. PLANNED and PUBLISHED are shown in this machine's time zone.

The campaign id is listed by 'oba otak campaign list'.

  oba otak campaign content list <campaignId>`

func newListCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "list <campaignId>",
		Short: "List the contents of a campaign",
		Long:  listHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runList,
	}
}

func runList(cmd *cobra.Command, args []string) error {
	campaignID := strings.TrimSpace(args[0])
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	contents, err := content.List(c, campaignID)
	if err != nil {
		return err
	}
	return content.PrintList(cmd.OutOrStdout(), contents)
}
