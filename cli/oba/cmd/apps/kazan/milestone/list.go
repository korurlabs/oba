package milestone

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
)

func newListCmd() *cobra.Command {
	var status string
	var related bool
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the milestones of the board",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			milestones, err := milestone.List(c, boardID, status, related)
			if err != nil {
				return err
			}
			return milestone.PrintList(cmd.OutOrStdout(), milestones, related)
		},
	}
	cmd.Flags().StringVar(&status, "status", "", "filter by status: OPEN or CLOSED")
	cmd.Flags().BoolVar(&related, "related", false, "include the milestones of related boards (parent, children, siblings)")
	return cmd
}
