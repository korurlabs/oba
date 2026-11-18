package milestone

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func newCloseCmd() *cobra.Command {
	return newStateChangeCmd("close <milestone>", "Close a milestone", "Closes a milestone.", client.MilestoneClose, "Closed")
}

func newReopenCmd() *cobra.Command {
	return newStateChangeCmd("reopen <milestone>", "Reopen a closed milestone", "Reopens a closed milestone.",
		client.MilestoneReopen, "Reopened")
}

func newRestoreCmd() *cobra.Command {
	return newStateChangeCmd("restore <milestoneId>", "Restore a deleted milestone",
		"Restores a deleted milestone. A deleted milestone is no longer listed, so pass its id. Ticket links are "+
			"not restored.", client.MilestoneRestore, "Restored")
}

// newStateChangeCmd builds one of the body-less state changes; done is the verb of the message.
func newStateChangeCmd(use, short, long, change, done string) *cobra.Command {
	return &cobra.Command{
		Use:   use,
		Short: short,
		Long:  long + "\n\n" + milestoneRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			m, err := milestone.ChangeState(c, boardID, milestoneArg(args), change)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "%s milestone %s\n", done, milestone.Label(m))
			return nil
		},
	}
}
