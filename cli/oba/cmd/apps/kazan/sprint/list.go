package sprint

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

func newListCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "list",
		Short: "List the sprints of the board",
		Args:  cobra.NoArgs,
		RunE:  runList,
	}
}

func runList(cmd *cobra.Command, _ []string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	sprints, err := sprint.List(c, boardID)
	if err != nil {
		return err
	}
	return sprint.PrintList(cmd.OutOrStdout(), sprints)
}
