// Package otak builds the oba otak command and adds the commands of each Otak domain, one
// package per domain under cmd/apps/otak.
package otak

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/otak/attachment"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/contact"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/event"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/find"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/icp"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/mailtemplate"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/outreach"
	"github.com/korurlabs/oba-cli/cmd/apps/otak/relation"
)

// NewCmd builds the oba otak command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "otak",
		Short: "Otak: the leads, customers and suppliers of your organization",
		Long: "Otak keeps the relations of the current organization: the companies it tries to win, sells to and buys from, " +
			"with the mail templates, outreaches and attachments it reaches out to them with.",
	}
	cmd.AddCommand(relation.NewCmd(), contact.NewCmd(), icp.NewCmd(), event.NewCmd(), campaign.NewCmd(), find.NewCmd(),
		mailtemplate.NewCmd(), outreach.NewCmd(), attachment.NewCmd())
	return cmd
}
