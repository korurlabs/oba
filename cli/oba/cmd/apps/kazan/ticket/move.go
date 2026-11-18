package ticket

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

const moveHelp = `Moves a ticket to a sprint (--sprint NAME, or --sprint sprint for the running one, else the
newest) or to the backlog (--backlog), optionally on another board (--target-board).

--sub-tickets says which sub-tickets go along: unfinished (the default: those not DONE and placed
with their parent), none, or comma-separated sub-ticket keys or ids. A move never detaches a
sub-ticket.

` + ticketRefHelp + `

  oba kazan ticket move OPS-42 --sprint "Sprint 3"
  oba kazan ticket move OPS-42 --backlog --target-board WEB
  oba kazan ticket move OPS-42 --sprint sprint --sub-tickets none`

func newMoveCmd() *cobra.Command {
	var in ticket.DestinationInput
	cmd := &cobra.Command{
		Use:   "move <ticket>",
		Short: "Move a ticket to a sprint or the backlog, optionally on another board",
		Long:  moveHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			readRetiredFlags(cmd, &in)
			dest, err := ticket.ParseDestination(in)
			if err != nil {
				return err
			}
			ref, err := ticketArg(args)
			if err != nil {
				return err
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			moved, err := ticket.Move(c, r, ref, dest)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Moved ticket %s to %s\n", ref, moved.Where)
			printCleared(cmd, moved)
			return nil
		},
	}
	addDestinationFlags(cmd, &in)
	return cmd
}

func newBulkMoveCmd() *cobra.Command {
	var in ticket.DestinationInput
	var tickets string
	cmd := &cobra.Command{
		Use:   "bulk-move",
		Short: "Move several tickets to a sprint or the backlog, optionally on another board",
		Long: "Moves the tickets --tickets names, comma separated keys or ids, like 'oba kazan ticket move'. " +
			"Without --target-board, every ticket must be on one board.\n\n" +
			"  oba kazan ticket bulk-move --tickets OPS-1,OPS-2 --sprint \"Sprint 3\"\n" +
			"  oba kazan ticket bulk-move --tickets OPS-1,WEB-7 --backlog --target-board OPS",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			readRetiredFlags(cmd, &in)
			dest, err := ticket.ParseDestination(in)
			if err != nil {
				return err
			}
			refs := userinput.SplitCSV(tickets)
			if len(refs) == 0 {
				return ticket.ErrNoTickets
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			moved, err := ticket.BulkMove(c, r, refs, dest)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Moved %d tickets to %s\n", moved.Count, moved.Where)
			printCleared(cmd, moved)
			return nil
		},
	}
	cmd.Flags().StringVar(&tickets, "tickets", "", "comma-separated ticket keys or ids (required)")
	addDestinationFlags(cmd, &in)
	return cmd
}

// printCleared reports the tickets a move detached from their milestone because the target board
// is not related to the milestone's board.
func printCleared(cmd *cobra.Command, moved ticket.Moved) {
	if len(moved.MilestoneCleared) > 0 {
		_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Milestone cleared (the target board is not related to the milestone's board): %s\n",
			strings.Join(moved.MilestoneCleared, ", "))
	}
}

// addDestinationFlags adds the destination and sub-ticket flags of move and bulk-move.
func addDestinationFlags(cmd *cobra.Command, in *ticket.DestinationInput) {
	f := cmd.Flags()
	f.StringVar(&in.SubTickets, "sub-tickets", "unfinished", "sub-tickets that move along: unfinished (not DONE and placed with their parent), none, or comma-separated sub-ticket keys or ids; a move never detaches a sub-ticket")
	f.StringVar(&in.Sprint, "sprint", "", "target sprint name, or 'sprint' for the running one (else the newest)")
	f.BoolVar(&in.Backlog, "backlog", false, "move to the backlog")
	f.StringVar(&in.TargetBoard, "target-board", "", "move to this board (name, key prefix or id); --sprint is looked up there")
	f.StringVar(&in.ToBoard, "to-board", "", "alias of --target-board")
	f.String("target-sprint", "", "removed: use --sprint NAME")
	f.String("target-backlog", "", "removed: use --backlog")
	_ = f.MarkHidden("to-board")
	_ = f.MarkHidden("target-sprint")
	_ = f.MarkHidden("target-backlog")
}

func readRetiredFlags(cmd *cobra.Command, in *ticket.DestinationInput) {
	in.TargetSprintGiven = cmd.Flags().Changed("target-sprint")
	in.TargetBacklogGiven = cmd.Flags().Changed("target-backlog")
}
