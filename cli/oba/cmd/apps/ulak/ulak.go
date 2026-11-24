// Package ulak builds the oba ulak command and adds the commands of each Ulak domain, one
// package per domain under cmd/apps/ulak.
package ulak

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/ulak/invite"
	"github.com/korurlabs/oba-cli/cmd/apps/ulak/member"
	"github.com/korurlabs/oba-cli/cmd/apps/ulak/org"
)

// NewCmd builds the oba ulak command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "ulak",
		Short: "Ulak: create organizations and manage who belongs to them",
		Long: "Ulak creates Oba organizations and manages their members and invitations. " +
			"Every rule on who may do what is the API's, checked against your role in the organization.",
	}
	cmd.AddCommand(org.NewCmd(), member.NewCmd(), invite.NewCmd())
	return cmd
}
