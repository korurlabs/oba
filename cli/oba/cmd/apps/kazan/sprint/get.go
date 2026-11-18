package sprint

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <sprint>",
		Short: "Show a sprint with its goal and boards",
		Long:  "Shows a sprint with its goal, the boards it is linked to and its dates.\n\n" + sprintRefHelp,
		RunE:  runGet,
	}
}

func runGet(cmd *cobra.Command, args []string) error {
	name, err := sprintArg(args)
	if err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	s, err := sprint.Get(c, boardID, name)
	if err != nil {
		return err
	}
	sprint.PrintDetails(cmd.OutOrStdout(), s)
	return nil
}
