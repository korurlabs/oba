package pr

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newReposCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "repos",
		Short: "List the repositories Kazan's GitHub App can read",
		Long: `List the GitHub repositories Kazan's GitHub App can read for the
organization, by name. Pass a repository's name, owner/name or ID to --repo of
'oba kazan pr pulls', 'share' and 'preview'.`,
		Example: `  oba kazan pr repos
  oba kazan pr repos --json`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			repos, err := pr.ListRepositories(c)
			if err != nil {
				return err
			}
			return pr.PrintRepositories(cmd.OutOrStdout(), repos)
		},
	}
}
