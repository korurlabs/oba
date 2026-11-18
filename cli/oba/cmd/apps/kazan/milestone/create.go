package milestone

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
)

const createHelp = `Creates a milestone on the board. Without --color it takes the board's colour.

  oba kazan milestone create --name "Public beta" --target-date 2026-12-01 --start-date 2026-10-15
  oba kazan milestone create --name "GA" --target-date 2027-01-15 --color "#22C55E"`

func newCreateCmd() *cobra.Command {
	var in milestone.CreateInput
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a milestone on the board",
		Long:  createHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := in.Check(); err != nil {
				return err
			}
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			created, err := milestone.Create(c, boardID, in)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created milestone %s\n", milestone.Label(created))
			return nil
		},
	}
	f := cmd.Flags()
	f.StringVar(&in.Name, "name", "", "milestone name (required)")
	f.StringVar(&in.TargetDate, "target-date", "", "target date, YYYY-MM-DD (required)")
	f.StringVar(&in.StartDate, "start-date", "", "start date, YYYY-MM-DD")
	f.StringVar(&in.Description, "description", "", "milestone description")
	f.StringVar(&in.Color, "color", "", "colour, #RRGGBB (default: the board's colour)")
	return cmd
}
