package pr

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <shareId>",
		Short: "Delete one of your shares and its card",
		Long: `Delete one of your shares; its card leaves the PR channel. Only the author can
delete a share. Without a terminal, or with --json, --yes is required.`,
		Example: `  oba kazan pr delete 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --yes`,
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			shareID, err := pr.ShareID(args[0])
			if err != nil {
				return err
			}
			if !yes {
				if !interactive() {
					return terminal.ErrDeleteNeedsYes
				}
				confirmed, err := prompter(cmd).Confirm(fmt.Sprintf("Delete share %s?", shareID))
				if err != nil {
					return err
				}
				if !confirmed {
					return cancelled(cmd)
				}
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			if err := pr.Delete(c, shareID); err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Deleted share %s\n", shareID)
			return nil
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "Delete without asking")
	return cmd
}
