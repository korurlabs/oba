package ticket

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <ticket>",
		Short: "Show a ticket by key (e.g. OPS-42) or id",
		Long:  "Shows every field of a ticket.\n\n" + ticketRefHelp,
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
			detail, err := ticket.Get(c, r, ref)
			if err != nil {
				return err
			}
			ticket.PrintDetails(cmd.OutOrStdout(), detail)
			return nil
		},
	}
}
