package sprint

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

const createHelp = `Creates a sprint on the board. Dates are YYYY-MM-DD (local midnight) or RFC 3339 timestamps.

The sprint can also run on child boards: --child-boards names them, --link-children takes every
child board you admin that has no active sprint. Without either, a terminal asks whether to link
those boards; a script links none.

  oba kazan sprint create --name "Sprint 5" --goal "Ship sign-in" --start-date 2026-11-23 --end-date 2026-12-04`

type createFlags struct {
	name, goal, description, startDate, endDate, childBoards string
	linkChildren                                             bool
}

func newCreateCmd() *cobra.Command {
	var f createFlags
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a sprint on the board",
		Long:  createHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runCreate(cmd, f)
		},
	}
	flags := cmd.Flags()
	flags.StringVar(&f.name, "name", "", "sprint name (required)")
	addGoalFlags(cmd, &f.goal, &f.description, "sprint goal")
	flags.StringVar(&f.startDate, "start-date", "", "start date (YYYY-MM-DD, local midnight, or an RFC 3339 timestamp)")
	flags.StringVar(&f.endDate, "end-date", "", "end date (YYYY-MM-DD, local midnight, or an RFC 3339 timestamp)")
	flags.BoolVar(&f.linkChildren, "link-children", false, "also link the sprint to every child board you admin that has no active sprint")
	flags.StringVar(&f.childBoards, "child-boards", "", "child boards to also link the sprint to (names, prefixes or ids, comma separated)")
	return cmd
}

func runCreate(cmd *cobra.Command, f createFlags) error {
	if cmd.Flags().Changed("link-children") && cmd.Flags().Changed("child-boards") {
		return sprint.ErrChildFlagsConflict
	}
	if strings.TrimSpace(f.name) == "" {
		return sprint.ErrNoName
	}
	in := sprint.CreateInput{Name: f.name, Goal: goalFlag(cmd, f.goal, f.description)}
	var err error
	if in.StartDate, err = instantFlag(f.startDate); err != nil {
		return err
	}
	if in.EndDate, err = instantFlag(f.endDate); err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	if in.ChildBoardIDs, err = childBoards(cmd, c, boardID, f); err != nil {
		return err
	}
	created, err := sprint.Create(c, boardID, in)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	_, _ = fmt.Fprintf(out, "Created sprint: %s\n", created.Name)
	if len(in.ChildBoardIDs) > 0 {
		_, _ = fmt.Fprintf(out, "Linked to %d child board(s)\n", len(in.ChildBoardIDs))
	}
	return nil
}

// childBoards returns the ids of the child boards the new sprint is also linked to:
// --child-boards, else with --link-children the preselected set, else on a terminal that set if
// the user agrees, else none, so that a script keeps the behaviour it had before child boards.
func childBoards(cmd *cobra.Command, c *client.Client, boardID string, f createFlags) ([]string, error) {
	if f.childBoards != "" {
		return sprint.ResolveBoards(f.childBoards, boardResolver(c))
	}
	if !f.linkChildren && !interactive() {
		return nil, nil
	}
	linkable, err := sprint.LinkableChildren(c, boardID)
	if err != nil {
		return nil, err
	}
	if f.linkChildren {
		if len(linkable) == 0 {
			_, _ = fmt.Fprintln(cmd.ErrOrStderr(), "No child board can be linked: none where you are an admin without an active sprint")
		}
		return sprint.ChildIDs(linkable), nil
	}
	if len(linkable) == 0 {
		return nil, nil
	}
	yes, err := confirm(cmd, sprint.ChildrenQuestion(linkable))
	if err != nil || !yes {
		return nil, err
	}
	return sprint.ChildIDs(linkable), nil
}

// instantFlag turns a typed date into the API's instant; empty stays empty.
func instantFlag(value string) (string, error) {
	if value == "" {
		return "", nil
	}
	return dates.ToAPIInstant(value)
}
