package cyberscore

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newCheckDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <check>",
		Short: "Delete a check",
		Long: "Deletes a check for good and recalculates the score without it. On a terminal it asks first, " +
			"unless --yes is given.\n\n" + checkRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runCheckDelete(cmd, args, yes)
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	return cmd
}

func runCheckDelete(cmd *cobra.Command, args []string, yes bool) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	ch, err := cyberscore.ResolveCheck(c, checkArg(args))
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes && interactive() {
		confirmed, err := confirm(cmd, fmt.Sprintf("Delete check %s?", cyberscore.Label(ch)))
		if err != nil {
			return err
		}
		if !confirmed {
			// The lookup before the question is not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := cyberscore.DeleteCheck(c, ch); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted check %s\n", cyberscore.Label(ch))
	return nil
}
