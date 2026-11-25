package cyberscore

import "github.com/spf13/cobra"

func newCheckCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "check",
		Short: "List, read, create, change and delete the checks behind the cyberscore",
		Long: "A check is one security control the cyberscore weighs: what is expected, its weight and category, " +
			"the outcome found and the status of the work on it. A check with outcome PASS, PARTIAL, FAIL or " +
			"NO_LICENSE counts toward the score; TODO and NOT_APPLICABLE do not.\n\n" + checkRefHelp,
	}
	cmd.AddCommand(
		newCheckListCmd(), newCheckGetCmd(), newCheckCreateCmd(), newCheckUpdateCmd(),
		newCheckOutcomeCmd(), newCheckStatusCmd(), newCheckDeleteCmd(),
	)
	return cmd
}
