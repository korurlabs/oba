package page

import (
	"bufio"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kalkan/page"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const pageDeleteHelp = `Deletes the page at <path> and every page below it, after asking for confirmation; without a
terminal to ask on, pass --yes. Kalkan soft-deletes them. Deleting a root page needs the MANAGE
level on it.

  oba kalkan page delete engineering/old-plans --yes`

func newDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <path>",
		Short: "Delete a page and every page below it",
		Long:  pageDeleteHelp + "\n\n" + pagePathHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args[0], yes)
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	return cmd
}

func runDelete(cmd *cobra.Command, rawPath string, yes bool) error {
	path, err := client.NormalizeKalkanPagePath(rawPath)
	if err != nil {
		return err
	}
	if !yes && !stdinIsTerminal() {
		return terminal.ErrDeleteNeedsYes
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	found, err := page.Find(c, path)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes {
		src := cmd.InOrStdin()
		question := fmt.Sprintf("Delete page %s and every page below it?", path)
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, question)
		if err != nil {
			return err
		}
		if !confirmed {
			// The tree read to find the page is not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := page.Delete(c, found); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted page %s and every page below it\n", path)
	return nil
}
