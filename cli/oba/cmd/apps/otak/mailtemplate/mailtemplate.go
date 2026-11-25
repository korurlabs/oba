// Package mailtemplate builds oba otak mail-template: list, upload and download the organization's
// mail templates as .md files.
package mailtemplate

import (
	"github.com/spf13/cobra"
)

// NewCmd builds oba otak mail-template with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "mail-template",
		Short: "List, upload and download the mail templates of the organization",
	}
	cmd.AddCommand(newListCmd(), newUploadCmd(), newDownloadCmd())
	return cmd
}
