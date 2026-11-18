package milestone

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
)

func newDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <milestone>",
		Short: "Delete a milestone and detach its tickets",
		Long: "Deletes a milestone and detaches its tickets; 'oba kazan milestone restore <id>' brings it back " +
			"without its ticket links. On a terminal it asks first, unless --yes is given.\n\n" + milestoneRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args, yes)
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	return cmd
}

func runDelete(cmd *cobra.Command, args []string, yes bool) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	m, err := milestone.Resolve(c, boardID, milestoneArg(args))
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes && interactive() {
		confirmed, err := confirm(cmd, fmt.Sprintf("Delete milestone %s and detach its tickets?", milestone.Label(m)))
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
	if err := milestone.Delete(c, boardID, m); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted milestone %s\n", milestone.Label(m))
	return nil
}
