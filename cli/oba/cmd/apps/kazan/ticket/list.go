package ticket

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// pagingRetired is shown when --page or --amount is passed: the ticket lists take no paging and
// return every match.
const pagingRetired = "the API returns the full list; the flag is ignored"

const listHelp = `Lists the tickets of the board --board names, else of the default board.

Without arguments, lists the backlog. With "sprint", lists the running sprint (the newest one
when none is running). With a sprint name, such as "Sprint 1", lists that sprint. With --milestone
and no sprint, lists the milestone's tickets on the board wherever they sit, sub-tickets included.

  oba kazan ticket list sprint --status TODO,IN_PROGRESS --assignee a@example.com`

func newListCmd() *cobra.Command {
	var in ticket.ListInput
	cmd := &cobra.Command{
		Use:   "list [sprint|<sprint-name>]",
		Short: "List the tickets of a board (the backlog, or a sprint)",
		Long:  listHelp,
		Args:  cobra.ArbitraryArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			in.Sprint = strings.Join(args, " ")
			return runList(cmd, in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&in.Assignee, "assignee", "", "filter by assignee emails or user ids (comma separated)")
	f.StringVar(&in.Project, "project", "", "filter by project id")
	f.BoolVar(&in.IncludeChildBoards, "include-child-boards", false, "also list the tickets of the board's related parent and child boards")
	f.StringVar(&in.Milestone, "milestone", "", "filter by milestone id or name; without a sprint it lists the milestone's tickets on the board")
	addFilterFlags(cmd, &in.Filters)
	return cmd
}

func runList(cmd *cobra.Command, in ticket.ListInput) error {
	if err := in.Check(); err != nil {
		return err
	}
	c, r, err := start(cmd)
	if err != nil {
		return err
	}
	listed, err := ticket.List(c, r, in)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	_, _ = fmt.Fprintf(out, "%s\n\n", listed.Header)
	if len(listed.Tickets) == 0 {
		_, _ = fmt.Fprintln(out, "No tickets found.")
		return nil
	}
	return ticket.PrintTable(out, listed.Tickets, in.Search)
}

func newAllCmd() *cobra.Command {
	var in ticket.AcrossInput
	cmd := &cobra.Command{
		Use:   "all",
		Short: "List tickets across all boards",
		Long: "Lists the tickets of every board you are a member of, or of the boards --boards names.\n\n" +
			"A --milestone name is resolved on --board or the default board; an id is used as given.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := in.Check(); err != nil {
				return err
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			tickets, err := ticket.ListAll(c, r, in)
			if err != nil {
				return err
			}
			return printAcross(cmd, tickets, in.Search, "No tickets found.")
		},
	}
	f := cmd.Flags()
	f.StringVar(&in.Assignee, "assignee", "", "filter by assignee emails or user ids (comma separated)")
	f.StringVar(&in.Boards, "boards", "", "only these boards: names, key prefixes or ids (comma separated)")
	f.StringVar(&in.Milestone, "milestone", "", "filter by milestone id or name (a name is resolved on --board)")
	addFilterFlags(cmd, &in.Filters)
	return cmd
}

func newMineCmd() *cobra.Command {
	var in ticket.AcrossInput
	cmd := &cobra.Command{
		Use:   "mine",
		Short: "List tickets assigned to me",
		Long:  "Lists the tickets assigned to you on every board you are a member of, or on the boards --boards names.",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := in.Check(); err != nil {
				return err
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			me, err := session.CurrentUserID()
			if err != nil {
				return err
			}
			tickets, err := ticket.ListMine(c, r, me, in)
			if err != nil {
				return err
			}
			return printAcross(cmd, tickets, in.Search, "No tickets assigned to you.")
		},
	}
	cmd.Flags().StringVar(&in.Boards, "boards", "", "only these boards: names, key prefixes or ids (comma separated)")
	addFilterFlags(cmd, &in.Filters)
	return cmd
}

func printAcross(cmd *cobra.Command, tickets []client.Ticket, search, empty string) error {
	out := cmd.OutOrStdout()
	if len(tickets) == 0 {
		_, _ = fmt.Fprintln(out, empty)
		return nil
	}
	return ticket.PrintTable(out, tickets, search)
}

// addFilterFlags adds the filter flags that ticket list, all and mine share.
func addFilterFlags(cmd *cobra.Command, f *ticket.Filters) {
	flags := cmd.Flags()
	flags.StringVar(&f.Status, "status", "", "filter by status (comma separated): "+strings.Join(ticket.Statuses, ", "))
	flags.StringVar(&f.Type, "type", "", "filter by type (comma separated): "+strings.Join(ticket.Types, ", "))
	flags.StringVar(&f.Priority, "priority", "", "filter by priority (comma separated): "+strings.Join(ticket.Priorities, ", "))
	flags.StringVar(&f.Reviewer, "reviewer", "", "filter by reviewer emails or user ids (comma separated)")
	flags.StringVar(&f.Label, "label", "", "filter by label ids (comma separated)")
	flags.StringVar(&f.Search, "search", "", "search tickets by title or key, case ignored; a ticket is also listed when a sub-ticket under it matches, and that sub-ticket is shown under it")
	flags.StringVar(&f.DueFrom, "due-from", "", "due on or after this day (YYYY-MM-DD)")
	flags.StringVar(&f.DueTo, "due-to", "", "due on or before this day (YYYY-MM-DD)")
	flags.StringVar(&f.SortBy, "sort-by", "", "sort field, such as createdAt or dueDate")
	flags.StringVar(&f.SortDir, "sort-dir", "", "sort direction: ASC or DESC")
	flags.Int("page", 0, "retired: the API returns the full list")
	flags.Int("amount", 100, "retired: the API returns the full list")
	_ = flags.MarkDeprecated("page", pagingRetired)
	_ = flags.MarkDeprecated("amount", pagingRetired)
}
