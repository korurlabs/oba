package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

const unlinkHelp = `Unlinks a sprint from this board. Its tickets on this board go to the backlog, to
--target-sprint with --tickets-to sprint, or are deleted with --delete-tickets. When this board
is the sprint's only board, the unlink deletes the sprint and needs --yes.

` + sprintRefHelp

func newUnlinkCmd() *cobra.Command {
	var opts sprint.UnlinkOptions
	cmd := &cobra.Command{
		Use:   "unlink <sprint>",
		Short: "Unlink a sprint from the board, moving or deleting its tickets",
		Long:  unlinkHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			// The default backlog is not a choice the user made, so --delete-tickets may go
			// without it.
			if !cmd.Flags().Changed("tickets-to") {
				opts.TicketsTo = ""
			}
			return runUnlink(cmd, args, opts)
		},
	}
	f := cmd.Flags()
	f.StringVar(&opts.TicketsTo, "tickets-to", "backlog", "where the sprint's tickets go: backlog or sprint (on this board)")
	f.StringVar(&opts.TargetSprint, "target-sprint", "", "sprint name for --tickets-to sprint")
	f.BoolVar(&opts.DeleteTickets, "delete-tickets", false, "delete the sprint's tickets instead of moving them")
	f.BoolVar(&opts.Yes, "yes", false, "confirm an unlink that deletes the sprint (this board is its only board)")
	return cmd
}

func runUnlink(cmd *cobra.Command, args []string, opts sprint.UnlinkOptions) error {
	name, err := sprintArg(args)
	if err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	unlinked, err := sprint.Unlink(c, boardID, name, opts)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Unlinked sprint %s from the board\n", sprint.Label(unlinked))
	return nil
}
