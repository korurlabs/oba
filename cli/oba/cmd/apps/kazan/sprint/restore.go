package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

func newRestoreCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "restore <sprint>",
		Short: "Restore a deleted sprint",
		Long: "Restores a deleted sprint. A deleted sprint is no longer listed, so name it by its id; a name " +
			"finds only a sprint that is still listed.",
		RunE: runRestore,
	}
}

func runRestore(cmd *cobra.Command, args []string) error {
	ref, err := sprintArg(args)
	if err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	id, label := ref, ref
	if !userinput.IsID(ref) {
		found, err := sprint.Find(c, boardID, ref)
		if err != nil {
			return err
		}
		id, label = found.ID, sprint.Label(found)
	}
	if err := sprint.Restore(c, boardID, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Restored sprint %s\n", label)
	return nil
}
