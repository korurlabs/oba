package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

func newStartCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "start <sprint>",
		Short: "Start a sprint",
		Long:  "Starts a planned sprint.\n\n" + sprintRefHelp,
		RunE:  runStart,
	}
}

func runStart(cmd *cobra.Command, args []string) error {
	name, err := sprintArg(args)
	if err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	started, err := sprint.Start(c, boardID, name)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Started sprint %s\n", sprint.Label(started))
	return nil
}
