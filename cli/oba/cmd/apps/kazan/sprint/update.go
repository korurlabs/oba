package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

const updateHelp = `Changes only the fields whose flags are given. A sprint of a repeating series is named after
its series: --name is refused for it, and --series-name renames the series and its unfinished
sprints. Dates are YYYY-MM-DD (local midnight) or RFC 3339 timestamps.

` + sprintRefHelp

func newUpdateCmd() *cobra.Command {
	var name, seriesName, goal, description, startDate, endDate string
	cmd := &cobra.Command{
		Use:   "update <sprint>",
		Short: "Change the given fields of a sprint",
		Long:  updateHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			in := sprint.UpdateInput{
				Name:       changed(cmd, "name", name),
				SeriesName: changed(cmd, "series-name", seriesName),
				Goal:       goalFlag(cmd, goal, description),
			}
			var err error
			if in.StartDate, err = changedInstant(cmd, "start-date", startDate); err != nil {
				return err
			}
			if in.EndDate, err = changedInstant(cmd, "end-date", endDate); err != nil {
				return err
			}
			return runUpdate(cmd, args, in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&name, "name", "", "new name; a sprint of a repeating series takes --series-name instead")
	f.StringVar(&seriesName, "series-name", "", "new series name; renames the series' unfinished sprints")
	addGoalFlags(cmd, &goal, &description, "new sprint goal")
	f.StringVar(&startDate, "start-date", "", "new start date (YYYY-MM-DD, local midnight, or an RFC 3339 timestamp)")
	f.StringVar(&endDate, "end-date", "", "new end date (YYYY-MM-DD, local midnight, or an RFC 3339 timestamp)")
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string, in sprint.UpdateInput) error {
	name, err := sprintArg(args)
	if err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	updated, err := sprint.Update(c, boardID, name, in)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated sprint: %s\n", updated.Name)
	return nil
}

// changedInstant is the API instant of a date flag that was given, else nil.
func changedInstant(cmd *cobra.Command, name, value string) (*string, error) {
	if !cmd.Flags().Changed(name) {
		return nil, nil
	}
	instant, err := instantFlag(value)
	if err != nil {
		return nil, err
	}
	return &instant, nil
}
