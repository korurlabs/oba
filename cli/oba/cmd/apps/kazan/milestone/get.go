package milestone

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <milestone>",
		Short: "Show a milestone",
		Long:  "Shows a milestone with its progress, health and dependencies.\n\n" + milestoneRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			m, err := milestone.Get(c, boardID, milestoneArg(args))
			if err != nil {
				return err
			}
			milestone.PrintDetails(cmd.OutOrStdout(), m)
			return nil
		},
	}
}
