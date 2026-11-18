// Package roadmap builds the oba kazan roadmap command: the milestones of a board on a timeline.
package roadmap

import (
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/term"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/roadmap"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const roadmapHelp = `Shows a board's milestones on a timeline, one lane per board.

[====] spans a milestone's start date to its target date, <> marks a milestone without a start
date, and | is today. A "<- name" line under a milestone names a milestone it depends on; "!"
marks a conflict: the milestone starts before that dependency is due. --children adds a lane for
each child board you are a member of. --from and --to keep the milestones whose span overlaps the
window. --json prints the API's roadmap unchanged.

  oba kazan --board Kagi roadmap --children
  oba kazan roadmap --from 2026-10-01 --to 2026-12-31 --json`

// width is the width of stdout, a variable so that tests draw at a fixed width.
var width = terminalWidth

// NewCmd builds the oba kazan roadmap command. It acts on the board of --board, else the default
// board.
func NewCmd() *cobra.Command {
	var opts roadmap.Options
	cmd := &cobra.Command{
		Use:   "roadmap",
		Short: "Show a board's milestones on a timeline",
		Long:  roadmapHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return run(cmd, opts)
		},
	}
	f := cmd.Flags()
	f.BoolVar(&opts.Children, "children", false, "add a lane for each child board")
	f.StringVar(&opts.From, "from", "", "window start, YYYY-MM-DD")
	f.StringVar(&opts.To, "to", "", "window end, YYYY-MM-DD")
	return cmd
}

func run(cmd *cobra.Command, opts roadmap.Options) error {
	if err := opts.Check(); err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	ref, err := boardflag.Selected(cmd, c)
	if err != nil {
		return err
	}
	r, err := roadmap.Get(c, ref.ID, opts)
	if err != nil {
		return err
	}
	// The today marker is the viewer's calendar day, as in the web roadmap, read at local
	// midnight like every milestone day. Milestone health comes from the API.
	today, err := dates.ParseDate(time.Now().Format(time.DateOnly))
	if err != nil {
		return fmt.Errorf("failed to resolve today's date: %w", err)
	}
	_, _ = fmt.Fprint(cmd.OutOrStdout(), roadmap.Render(r, width(), today))
	return nil
}

// terminalWidth is the width of stdout, or roadmap.DefaultWidth when stdout is not a terminal.
func terminalWidth() int {
	w, _, err := term.GetSize(int(os.Stdout.Fd()))
	if err != nil || w <= 0 {
		return roadmap.DefaultWidth
	}
	return w
}
