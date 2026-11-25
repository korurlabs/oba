// Package kalkan builds the oba kalkan command and adds the commands of each Kalkan domain, one
// package per domain under cmd/apps/kalkan.
package kalkan

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kalkan/attachment"
	"github.com/korurlabs/oba-cli/cmd/apps/kalkan/page"
	"github.com/korurlabs/oba-cli/cmd/apps/kalkan/upload"
)

// NewCmd builds the oba kalkan command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "kalkan",
		Short: "Kalkan: the docs and ISMS pages of your organization",
		Long: "Kalkan keeps the pages of the current organization: its docs and the documents of its ISMS, as a tree " +
			"of Markdown pages in Turkish and English, each named by its path of slugs such as engineering/plans.",
	}
	cmd.AddCommand(page.NewCmd(), upload.NewCmd(), attachment.NewCmd())
	return cmd
}
