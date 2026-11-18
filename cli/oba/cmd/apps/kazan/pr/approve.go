package pr

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newApproveCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "approve <shareId>",
		Short: "Approve a share you review",
		Long: `Approve a share on which you are a reviewer. Approving again changes nothing.
Only a reviewer of the share can approve it.`,
		Example: `  oba kazan pr approve 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d`,
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runApproval(cmd, args[0], true)
		},
	}
}

func newWithdrawCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "withdraw <shareId>",
		Short: "Withdraw your approval of a share",
		Long: `Withdraw your approval of a share on which you are a reviewer. Withdrawing an
approval you have not given changes nothing.`,
		Example: `  oba kazan pr withdraw 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d`,
		Args:    cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runApproval(cmd, args[0], false)
		},
	}
}

// runApproval sets or clears the caller's approval.
func runApproval(cmd *cobra.Command, arg string, approve bool) error {
	shareID, err := pr.ShareID(arg)
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	if err := pr.SetApproval(c, shareID, approve); err != nil {
		return err
	}
	if approve {
		_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Approved %s\n", shareID)
	} else {
		_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Withdrew approval on %s\n", shareID)
	}
	return nil
}
