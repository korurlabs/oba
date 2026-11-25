package cyberscore

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newShowCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "show",
		Short: "Show the cyberscore, its level and its category breakdown",
		Long: "Shows the live cyberscore of the organization: the percentage and level, the number of checks " +
			"with a scorable outcome, when it was last calculated and one percentage per category.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			score, err := cyberscore.Show(c)
			if err != nil {
				return err
			}
			return cyberscore.PrintScore(cmd.OutOrStdout(), score)
		},
	}
}
