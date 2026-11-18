package ticket

import (
	"encoding/json"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

func newBulkDeleteCmd() *cobra.Command {
	var tickets string
	var yes bool
	cmd := &cobra.Command{
		Use:   "bulk-delete",
		Short: "Delete several tickets",
		Long: "Deletes the tickets --tickets names, comma separated keys or ids, each on its own board. " +
			"Every ticket is looked up first and the deletion is confirmed once; without a terminal, --yes confirms it. " +
			"A ticket that fails does not stop the others.\n\n" +
			"With --json, the output is [{\"key\", \"ok\", \"error\"}] for each ticket.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			refs := userinput.SplitCSV(tickets)
			if len(refs) == 0 {
				return ticket.ErrNoTickets
			}
			if !yes && !interactive() {
				return fmt.Errorf("ticket bulk-delete deletes %d tickets: pass --yes to confirm in non-interactive mode", len(refs))
			}
			c, r, err := start(cmd)
			if err != nil {
				return err
			}
			results := ticket.ResolveForDelete(r, refs)
			if !yes {
				confirmed, err := confirm(cmd, fmt.Sprintf("Delete %d tickets?", ticket.Resolved(results)))
				if err != nil {
					return err
				}
				if !confirmed {
					jsonresult.Nothing()
					_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Cancelled.")
					return nil
				}
			}
			failed := ticket.DeleteResolved(c.Unrecorded(), results)
			for _, res := range results {
				if res.OK {
					_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Deleted ticket %s\n", res.Key)
				} else {
					_, _ = fmt.Fprintf(cmd.ErrOrStderr(), "Failed to delete ticket %s: %s\n", res.Key, res.Error)
				}
			}
			if jsonout.Enabled() {
				encoded, err := json.Marshal(results)
				if err != nil {
					return fmt.Errorf("failed to encode the JSON output: %w", err)
				}
				if err := jsonresult.SetPartial(encoded); err != nil {
					return err
				}
			}
			if failed > 0 {
				cmd.SilenceUsage = true
				return fmt.Errorf("%d of %d tickets could not be deleted", failed, len(results))
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&tickets, "tickets", "", "comma-separated ticket keys or ids (required)")
	cmd.Flags().BoolVar(&yes, "yes", false, "confirm the deletion without a prompt")
	return cmd
}
