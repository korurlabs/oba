package ticket

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
)

func newOrderCmd() *cobra.Command {
	var order int
	var sprintRetired, backlogRetired string
	cmd := &cobra.Command{
		Use:   "order <ticket>",
		Short: "Reorder a ticket within its sprint or backlog",
		Long:  "Moves a ticket to position --order within its own sprint or backlog.\n\n" + ticketRefHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			ref, err := ticketArg(args)
			if err != nil {
				return err
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			target, err := ticket.Order(c, r, ref, order)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Reordered ticket %s to position %d\n", target.ID, order)
			return nil
		},
	}
	f := cmd.Flags()
	f.IntVar(&order, "order", 0, "new display order (required)")
	f.StringVar(&sprintRetired, "sprint", "", "retired: the ticket's own sprint or backlog is used")
	f.StringVar(&backlogRetired, "backlog", "", "retired: the ticket's own sprint or backlog is used")
	_ = f.MarkDeprecated("sprint", "the ticket's own sprint or backlog is used; the flag is ignored")
	_ = f.MarkDeprecated("backlog", "the ticket's own sprint or backlog is used; the flag is ignored")
	_ = cmd.MarkFlagRequired("order")
	return cmd
}
