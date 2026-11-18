package pr

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <shareId>",
		Short: "Print the card text of a share",
		Long: `Print the card text of a share on stdout, the same for every reader. Its
status, author, reviewers, related tickets, links and ID go to stderr, so
stdout can be piped. The Kazan link is printed only when ` + pr.EnvWebURL + `
names the origin of the Kazan web app.`,
		Example: `  oba kazan pr get 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d
  oba kazan pr get 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --json`,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			shareID, err := pr.ShareID(args[0])
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			share, _, err := pr.Get(c, shareID)
			if err != nil {
				return err
			}
			pr.PrintCard(cmd.OutOrStdout(), share.SummaryText)
			pr.PrintBlock(cmd.ErrOrStderr(), share, false)
			return nil
		},
	}
}
