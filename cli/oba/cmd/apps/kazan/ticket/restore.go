package ticket

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
)

func newRestoreCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "restore <ticket-id>",
		Short: "Restore a deleted ticket by its id (with --board)",
		Long: "Restores a deleted ticket. A deleted ticket is no longer found by its key, so name it by its id; " +
			"its board is --board or the default board.",
		Args: cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := ticketArg(args)
			if err != nil {
				return err
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			if err := ticket.Restore(c, r, id); err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Restored ticket %s\n", id)
			return nil
		},
	}
}
