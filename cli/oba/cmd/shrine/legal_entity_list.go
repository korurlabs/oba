package shrine

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/legalentity"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newLegalEntityListCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "list",
		Short: "List the legal entities of the organization",
		Long:  "Lists the legal entities by short name: short name, legal name, country, registration number, last change and id.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			entities, err := legalentity.List(c)
			if err != nil {
				return err
			}
			return legalentity.PrintList(cmd.OutOrStdout(), entities)
		},
	}
}
