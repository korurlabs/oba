package campaign

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const updateHelp = `Changes only the fields given in one JSON object on stdin; every other field keeps its value.
null clears a field. The keys are the API's field names; the values go to the API unchanged, and
a key the API does not know is refused before anything is sent. The contents stay as they are;
'oba otak campaign content update' changes one.

  oba otak campaign update <id> <<'EOF'
  {"endDate": "2027-04-15", "icpId": null}
  EOF`

func newUpdateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "update <id>",
		Short: "Change the given fields of a campaign from one JSON object on stdin",
		Long:  updateHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runUpdate,
	}
	cmd.SetHelpFunc(fieldHelp(updateHelp, campaign.UpdateFields, false))
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string) error {
	id := strings.TrimSpace(args[0])
	in, err := campaignStdin(cmd)
	if err != nil {
		return err
	}
	given, err := terminal.ReadStdinObject(in)
	if err != nil {
		return err
	}
	if len(given) == 0 {
		return terminal.ErrNothingToUpdate
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	updated, err := campaign.Update(c, id, given)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated campaign %s (%s)\n", id, guardrail.OneLine(updated.Name))
	return nil
}
