package pr

import (
	"errors"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newPullsCmd() *cobra.Command {
	var repo, search, state, author string
	var page int
	cmd := &cobra.Command{
		Use:   "pulls",
		Short: "List a repository's pull requests, last updated first",
		Long: `List a repository's pull requests as GitHub returns them, last updated first,
30 per page.

--repo takes the repository's name, owner/name or ID ('oba kazan pr repos'
lists them). --search "379" or "#379" finds that pull request; other text
searches titles and descriptions, at most 100 characters.

--state takes OPEN, DRAFT, MERGED or CLOSED, case-insensitive. These are
GitHub's states, not a share's status. --author takes GitHub logins, at most 5,
such as dependabot[bot]; unlike 'oba kazan pr list' it takes no email, and
"me" is read as a login. Both are comma-separated. A pull request passes with
any of the values of a flag and must pass both flags. A number in --search
ignores both.

Filters may use GitHub search, which allows 30 searches a minute and shows the
first 1000 results.`,
		Example: `  oba kazan pr pulls --repo shop
  oba kazan pr pulls --repo acme/shop --search "#688"
  oba kazan pr pulls --repo shop --search sidebar --page 2
  oba kazan pr pulls --repo shop --state open,merged --author ada-dev,bob-dev`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			repoRef := strings.TrimSpace(repo)
			if repoRef == "" {
				return errors.New("--repo is required: pass a repository's name, owner/name or ID ('oba kazan pr repos' lists them)")
			}
			filter := pr.PullFilter{Search: strings.TrimSpace(search), Page: page}
			if err := pr.CheckSearch(filter.Search); err != nil {
				return err
			}
			if page < 1 {
				return fmt.Errorf("--page must be 1 or more, got %d", page)
			}
			var err error
			for _, raw := range userinput.SplitCSV(state) {
				value, err := ticket.EnumValue("state", raw, pr.PullStates)
				if err != nil {
					return err
				}
				filter.States = append(filter.States, value)
			}
			if filter.Authors, err = pr.ParseLogins(author); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			result, err := pr.ListPulls(c, repoRef, filter)
			if err != nil {
				return err
			}
			return pr.PrintPulls(cmd.OutOrStdout(), result, page)
		},
	}
	f := cmd.Flags()
	f.StringVar(&repo, "repo", "", "Repository: name, owner/name or ID (required)")
	f.StringVar(&search, "search", "", "\"379\" or \"#379\" finds that number; other text searches titles")
	f.StringVar(&state, "state", "", "GitHub states (comma-separated): OPEN, DRAFT, MERGED, CLOSED")
	f.StringVar(&author, "author", "", fmt.Sprintf("GitHub logins (comma-separated, at most %d)", pr.MaxPullAuthors))
	f.IntVar(&page, "page", 1, "Page number, from 1")
	return cmd
}
