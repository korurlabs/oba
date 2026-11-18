package updates

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newListCmd() *cobra.Command {
	in := updates.ListInput{Page: updates.DefaultPage, Amount: updates.DefaultAmount}
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the daily updates you can see, newest first",
		Long: `List the daily updates you can see, newest first: yours and those of everyone
who shares an active organization with you.

--author takes "me", emails or user ids, comma-separated. --boards keeps
updates with a line on one of those boards. --from and --to are inclusive
report days: the report day (Europe/Istanbul) is the Istanbul calendar day on
which an update was posted.`,
		Example: `  oba kazan updates list
  oba kazan updates list --author me --from 2026-10-01
  oba kazan updates list --author ada@example.com,bob@example.com --boards OPS`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := in.Check(); err != nil {
				return err
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			page, err := updates.List(c, r, in, session.CurrentUserID)
			if err != nil {
				return err
			}
			return updates.PrintPage(cmd.OutOrStdout(), page)
		},
	}
	flags := cmd.Flags()
	flags.StringVar(&in.Author, "author", "", "authors: me, emails or user ids (comma separated)")
	flags.StringVar(&in.Boards, "boards", "", "boards: names, key prefixes or ids (comma separated)")
	flags.StringVar(&in.From, "from", "", "first report day (Europe/Istanbul), inclusive (YYYY-MM-DD)")
	flags.StringVar(&in.To, "to", "", "last report day (Europe/Istanbul), inclusive (YYYY-MM-DD)")
	flags.IntVar(&in.Page, "page", updates.DefaultPage, "page number, from 1")
	flags.IntVar(&in.Amount, "amount", updates.DefaultAmount, fmt.Sprintf("updates per page, at most %d", updates.MaxPageSize))
	return cmd
}
