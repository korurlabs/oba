package cyberscore

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newCheckStatusCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "status <check> <STATUS>",
		Short: "Set the status of the work on a check",
		Long: "Sets the status of a check and changes nothing else; the score does not move. The status is " +
			"the last argument: " + strings.Join(cyberscore.Statuses, ", ") + ".\n\n" +
			"  oba kazan cyberscore check status MFA enforced in-progress\n\n" + checkRefHelp,
		Args: cobra.MinimumNArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			last := len(args) - 1
			if _, err := cyberscore.ParseStatus(args[last]); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			ch, err := cyberscore.SetStatus(c, checkArg(args[:last]), args[last])
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Check %s: status %s\n", cyberscore.Label(ch), ch.Status)
			return nil
		},
	}
}
