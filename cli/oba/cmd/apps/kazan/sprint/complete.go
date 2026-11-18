package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

const completeHelp = `Completes the running sprint. Its unfinished tickets go to the backlog of this board, or of
--target-board; --tickets-to sprint moves them to --target-sprint of that board, and
--tickets-to delete deletes them, which needs --yes.

  oba kazan sprint complete sprint --tickets-to sprint --target-sprint "Sprint 6"

` + sprintRefHelp

func newCompleteCmd() *cobra.Command {
	var opts sprint.CompleteOptions
	cmd := &cobra.Command{
		Use:   "complete <sprint>",
		Short: "Complete a sprint",
		Long:  completeHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runComplete(cmd, args, opts)
		},
	}
	f := cmd.Flags()
	f.StringVar(&opts.TicketsTo, "tickets-to", "backlog", "where unfinished tickets go: backlog, sprint or delete")
	f.StringVar(&opts.TargetSprint, "target-sprint", "", "sprint name for --tickets-to sprint, on the target board")
	f.StringVar(&opts.TargetBoard, "target-board", "", "board name, prefix or id to move unfinished tickets to (default: this board)")
	f.BoolVar(&opts.Yes, "yes", false, "confirm --tickets-to delete")
	return cmd
}

func runComplete(cmd *cobra.Command, args []string, opts sprint.CompleteOptions) error {
	name, err := sprintArg(args)
	if err != nil {
		return err
	}
	if _, err := opts.Check(); err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	completed, err := sprint.Complete(c, boardID, name, opts, boardResolver(c))
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Completed sprint %s\n", sprint.Label(completed))
	return nil
}
