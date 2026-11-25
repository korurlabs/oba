package cyberscore

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const checkCreateHelp = `Creates a check. --name, --expected and --weight are required; a name is unique in the
organization. Without --outcome the check starts as TODO; without --status the API derives it
from the outcome.
  oba kazan cyberscore check create --name "MFA enforced" --expected "Every account signs in with MFA" \
    --weight 10 --category identity --sort-order 1`

func newCheckCreateCmd() *cobra.Command {
	var in cyberscore.CreateInput
	var weight, sortOrder int
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a check",
		Long:  checkCreateHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			in.Weight = changedInt(cmd, "weight", weight)
			in.SortOrder = changedInt(cmd, "sort-order", sortOrder)
			if err := in.Check(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			created, err := cyberscore.CreateCheck(c, in)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created check %s\n", cyberscore.Label(created))
			return nil
		},
	}
	f := cmd.Flags()
	f.StringVar(&in.Name, "name", "", "check name, unique in the organization (required)")
	f.StringVar(&in.Expected, "expected", "", "the expected result (required)")
	f.IntVar(&weight, "weight", 0, "weight in the score, 1 to 100 (required)")
	f.StringVar(&in.Description, "description", "", "description")
	f.IntVar(&sortOrder, "sort-order", 0, "position within its category, 0 to 9999 (default 0)")
	f.StringVar(&in.Category, "category", "", "category: "+strings.Join(cyberscore.Categories, ", "))
	f.StringVar(&in.Outcome, "outcome", "", "outcome: "+strings.Join(cyberscore.Outcomes, ", ")+" (default TODO)")
	f.StringVar(&in.Status, "status", "", "status: "+strings.Join(cyberscore.Statuses, ", ")+" (default: from the outcome)")
	return cmd
}
