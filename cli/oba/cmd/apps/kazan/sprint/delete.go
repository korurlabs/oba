package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

const deleteHelp = `Deletes a sprint; 'oba kazan sprint restore <id>' brings it back. Its tickets go to this
board's backlog, to --target-sprint with --tickets-to sprint, or are deleted with --tickets-to
delete, which needs --yes. On a terminal it asks first, unless --yes is given.

` + sprintRefHelp

func newDeleteCmd() *cobra.Command {
	var opts sprint.DeleteOptions
	cmd := &cobra.Command{
		Use:   "delete <sprint>",
		Short: "Delete a sprint",
		Long:  deleteHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args, opts)
		},
	}
	f := cmd.Flags()
	f.StringVar(&opts.TicketsTo, "tickets-to", "backlog", "where the sprint's tickets go: backlog, sprint or delete")
	f.StringVar(&opts.TargetSprint, "target-sprint", "", "sprint name for --tickets-to sprint, on this board")
	f.BoolVarP(&opts.Yes, "yes", "y", false, "confirm --tickets-to delete, and delete without asking")
	return cmd
}

func runDelete(cmd *cobra.Command, args []string, opts sprint.DeleteOptions) error {
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
	found, err := sprint.Find(c, boardID, name)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !opts.Yes && interactive() {
		confirmed, err := confirm(cmd, fmt.Sprintf("Delete sprint '%s'?", found.Name))
		if err != nil {
			return err
		}
		if !confirmed {
			// The lookups before the question are not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := sprint.Delete(c, boardID, found, opts); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted sprint %s\n", sprint.Label(found))
	return nil
}
