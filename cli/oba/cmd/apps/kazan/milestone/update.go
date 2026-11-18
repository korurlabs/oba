package milestone

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
)

const updateHelp = `Changes only the fields whose flags are given.

--start-date "" clears the start date and --description "" clears the description. --color takes
#RRGGBB; a colour cannot be cleared.

` + milestoneRefHelp

func newUpdateCmd() *cobra.Command {
	var name, description, startDate, targetDate, color string
	cmd := &cobra.Command{
		Use:   "update <milestone>",
		Short: "Change the given fields of a milestone",
		Long:  updateHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			in := milestone.UpdateInput{
				Name:        changed(cmd, "name", name),
				Description: changed(cmd, "description", description),
				StartDate:   changed(cmd, "start-date", startDate),
				TargetDate:  changed(cmd, "target-date", targetDate),
				Color:       changed(cmd, "color", color),
			}
			if _, err := in.Request(); err != nil {
				return err
			}
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			updated, err := milestone.Update(c, boardID, milestoneArg(args), in)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated milestone %s\n", milestone.Label(updated))
			return nil
		},
	}
	f := cmd.Flags()
	f.StringVar(&name, "name", "", "new name")
	f.StringVar(&description, "description", "", `new description ("" clears it)`)
	f.StringVar(&startDate, "start-date", "", `new start date, YYYY-MM-DD ("" clears it)`)
	f.StringVar(&targetDate, "target-date", "", "new target date, YYYY-MM-DD")
	f.StringVar(&color, "color", "", "new colour, #RRGGBB")
	return cmd
}
