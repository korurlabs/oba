// Package admin builds the oba admin command and adds the commands of each admin domain, one
// package per domain under cmd/apps/admin.
package admin

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/admin/invite"
	"github.com/korurlabs/oba-cli/cmd/apps/admin/member"
	"github.com/korurlabs/oba-cli/cmd/apps/admin/org"
)

// NewCmd builds the oba admin command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "admin",
		Short: "Admin: create organizations and manage who belongs to them",
		Long: "Admin creates Oba organizations and manages their members and invitations. " +
			"Every rule on who may do what is the API's, checked against your role in the organization.",
	}
	cmd.AddCommand(org.NewCmd(), member.NewCmd(), invite.NewCmd())
	return cmd
}
