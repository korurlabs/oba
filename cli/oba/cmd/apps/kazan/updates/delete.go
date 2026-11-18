package updates

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <updateId>",
		Short: "Delete one of your daily updates",
		Long: `Delete one of your daily updates. Ticket progress it set is not reverted.
Without a terminal, or with --json, --yes is required.`,
		Example: `  oba kazan updates delete 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --yes`,
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := updates.ParseID(args[0])
			if err != nil {
				return err
			}
			if !yes {
				p := prompter(cmd)
				if p == nil {
					return terminal.ErrDeleteNeedsYes
				}
				confirmed, err := p.Confirm(fmt.Sprintf("Delete update %s?", id))
				if err != nil {
					return err
				}
				if !confirmed {
					return cancelled(cmd)
				}
			}
			// An author's updates span organizations, so the delete needs none chosen.
			c, err := session.UserClient()
			if err != nil {
				return err
			}
			if err := c.DeleteDailyUpdate(id); err != nil {
				return fmt.Errorf("failed to delete update: %w", err)
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Deleted update %s\n", id)
			return nil
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking")
	return cmd
}
