package shrine

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/legalentity"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newLegalEntityGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <id>",
		Short: "Show a legal entity with every field",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := legalentity.ParseID(args[0])
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			e, err := legalentity.Get(c, id)
			if err != nil {
				return err
			}
			legalentity.PrintDetails(cmd.OutOrStdout(), e)
			return nil
		},
	}
}
