package sprint

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
)

func newLinkCmd() *cobra.Command {
	var toBoards string
	cmd := &cobra.Command{
		Use:   "link <sprint>",
		Short: "Link a sprint to other boards",
		Long: "Links a sprint of this board to the boards of --to-board, so that it runs there too. To put " +
			"tickets in a sprint use 'oba kazan ticket move <ticket> --sprint <name>'.\n\n" + sprintRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLink(cmd, args, toBoards)
		},
	}
	cmd.Flags().StringVar(&toBoards, "to-board", "", "boards to link the sprint to (names, prefixes or ids, comma separated)")
	return cmd
}

func runLink(cmd *cobra.Command, args []string, toBoards string) error {
	name, err := sprintArg(args)
	if err != nil {
		return err
	}
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	linked, ids, err := sprint.Link(c, boardID, name, toBoards, boardResolver(c))
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Linked sprint %s to %d board(s)\n", sprint.Label(linked), len(ids))
	return nil
}
