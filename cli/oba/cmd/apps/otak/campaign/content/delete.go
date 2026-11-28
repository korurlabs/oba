package content

import (
	"bufio"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign/content"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const deleteHelp = `Deletes a content of a campaign with its images after asking for confirmation. Without a
terminal to ask on, pass --yes. Any member of the organization may delete a content.

  oba otak campaign content delete <campaignId> <contentId> --yes`

func newDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <campaignId> <contentId>",
		Short: "Delete a content of a campaign",
		Long:  deleteHelp,
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, strings.TrimSpace(args[0]), strings.TrimSpace(args[1]), yes)
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	return cmd
}

func runDelete(cmd *cobra.Command, campaignID, contentID string, yes bool) error {
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
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete content %s?", contentID))
		if err != nil {
			return err
		}
		if !confirmed {
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := content.Delete(c, campaignID, contentID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted content %s\n", contentID)
	return nil
}
