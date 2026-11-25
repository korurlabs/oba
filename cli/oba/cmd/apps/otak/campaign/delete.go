package campaign

import (
	"bufio"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const deleteHelp = `Deletes a campaign with its contents and their images after asking for confirmation; the outreaches and
relations linked to it stay and lose the link. Without a terminal to ask on, pass --yes. Only an
admin or owner of the organization may delete.

  oba otak campaign delete <id> --yes`

func newDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete a campaign; needs the ADMIN role",
		Long:  deleteHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, strings.TrimSpace(args[0]), yes)
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	return cmd
}

func runDelete(cmd *cobra.Command, id string, yes bool) error {
	if !yes && !stdinIsTerminal() {
		return terminal.ErrDeleteNeedsYes
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes {
		src := cmd.InOrStdin()
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete campaign %s?", id))
		if err != nil {
			return err
		}
		if !confirmed {
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := campaign.Delete(c, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted campaign %s\n", id)
	return nil
}
