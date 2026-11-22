package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/shrine/legalentity"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newLegalEntityDeleteCmd() *cobra.Command {
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete a legal entity",
		Long:  "Deletes a legal entity. On a terminal it asks first, unless --yes is given.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLegalEntityDelete(cmd, args[0], yes)
		},
	}
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	return cmd
}

func runLegalEntityDelete(cmd *cobra.Command, arg string, yes bool) error {
	id, err := legalentity.ParseID(arg)
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	label := id
	if !yes && interactive() {
		// The entity is read only to name it in the question.
		e, err := legalentity.Get(c, id)
		if err != nil {
			return err
		}
		label = legalentity.Label(e)
		confirmed, err := confirm(cmd, fmt.Sprintf("Delete legal entity %s?", label))
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
	if err := legalentity.Delete(c, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted legal entity %s\n", label)
	return nil
}
