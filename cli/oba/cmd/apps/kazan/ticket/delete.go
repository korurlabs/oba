package ticket

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
)

func newDeleteCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "delete <ticket>",
		Short: "Delete a ticket by key (e.g. OPS-42) or id",
		Long: "Deletes a ticket; 'oba kazan ticket restore <id>' brings it back. On a terminal it asks first.\n\n" +
			ticketRefHelp,
		Args: cobra.MaximumNArgs(1),
		RunE: runDelete,
	}
}

func runDelete(cmd *cobra.Command, args []string) error {
	ref, err := ticketArg(args)
	if err != nil {
		return err
	}
	c, r, err := start(cmd)
	if err != nil {
		return err
	}
	target, err := r.Ticket(ref)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if interactive() {
		confirmed, err := confirm(cmd, fmt.Sprintf("Delete ticket %s?", ref))
		if err != nil {
			return err
		}
		if !confirmed {
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := ticket.Delete(c, target); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted ticket %s\n", ref)
	return nil
}
