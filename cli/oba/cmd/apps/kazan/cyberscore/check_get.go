package cyberscore

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newCheckGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <check>",
		Short: "Show a check",
		Long:  "Shows a check with its expected result and description.\n\n" + checkRefHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			ch, err := cyberscore.ResolveCheck(c, checkArg(args))
			if err != nil {
				return err
			}
			// The check comes from a list read kept out of --json, so its item is the result.
			if err := jsonresult.Set(ch.Raw); err != nil {
				return err
			}
			cyberscore.PrintCheck(cmd.OutOrStdout(), ch)
			return nil
		},
	}
}
