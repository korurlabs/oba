package milestone

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const dependHelp = `Records that a milestone depends on another: it should not start before the other one's
target date. Both milestones are an id or a name. The dependency must be on the same board or a
related one (parent, child, sibling), and a dependency that closes a cycle is rejected. Dates are
never shifted; a milestone that starts before its dependency is due is flagged as a conflict.

  oba kazan milestone depend "GA" --on "Public beta"`

func newDependCmd() *cobra.Command {
	return newDependencyCmd("depend <milestone> --on <milestone>", "Record that a milestone depends on another",
		dependHelp, milestone.Depend, "now depends on")
}

func newUndependCmd() *cobra.Command {
	return newDependencyCmd("undepend <milestone> --on <milestone>", "Remove a dependency between two milestones",
		"Removes the dependency of a milestone on the one --on names.\n\n  oba kazan milestone undepend \"GA\" --on \"Public beta\"",
		milestone.Undepend, "no longer depends on")
}

// newDependencyCmd builds depend or undepend; relation words the message.
func newDependencyCmd(use, short, long string,
	change func(c *client.Client, boardID, arg, on string) (milestone.Dependency, error), relation string) *cobra.Command {
	var on string
	cmd := &cobra.Command{
		Use:   use,
		Short: short,
		Long:  long + "\n\n" + milestoneRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			result, err := change(c, boardID, milestoneArg(args), on)
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			_, _ = fmt.Fprintf(out, "Milestone %s %s %s\n", milestone.Label(result.Dependent), relation, milestone.Label(result.On))
			milestone.PrintDependsOn(out, result.Dependent.DependsOn)
			if d, ok := result.Conflict(); ok {
				_, _ = fmt.Fprintf(out, "Warning: %s starts before %q is due on %s. Dates were not changed.\n",
					milestone.Label(result.Dependent), guardrail.OneLine(d.Name), d.TargetDate)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&on, "on", "", "the milestone it depends on: id or name (required)")
	return cmd
}
