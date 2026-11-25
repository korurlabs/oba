package cyberscore

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newCheckOutcomeCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "outcome <check> <OUTCOME>",
		Short: "Record the outcome of a check",
		Long: "Records the outcome of a check; the API sets its status from the outcome (TODO: NOT_STARTED, " +
			"NOT_APPLICABLE: NOT_APPLICABLE, any other: DONE) and recalculates the score. The outcome is the " +
			"last argument: " + strings.Join(cyberscore.Outcomes, ", ") + ".\n\n" +
			"  oba kazan cyberscore check outcome MFA enforced pass\n\n" + checkRefHelp,
		Args: cobra.MinimumNArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			last := len(args) - 1
			if _, err := cyberscore.ParseOutcome(args[last]); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			ch, err := cyberscore.SetOutcome(c, checkArg(args[:last]), args[last])
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Check %s: outcome %s, status %s\n", cyberscore.Label(ch), ch.Outcome, ch.Status)
			return nil
		},
	}
}
