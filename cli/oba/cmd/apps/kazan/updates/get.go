package updates

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <updateId>",
		Short: "Print the text of a daily update",
		Long: `Print the paste-ready text of a daily update, the same for every reader. With
--json, each line carries canOpen: whether you may open its ticket.`,
		Example: `  oba kazan updates get 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d | pbcopy`,
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := updates.ParseID(args[0])
			if err != nil {
				return err
			}
			// An author's updates span organizations, so the read needs none chosen.
			c, err := session.UserClient()
			if err != nil {
				return err
			}
			update, err := c.GetDailyUpdate(id)
			if err != nil {
				return fmt.Errorf("failed to get update: %w", err)
			}
			updates.PrintSummary(cmd.OutOrStdout(), update.SummaryText)
			return nil
		},
	}
}
