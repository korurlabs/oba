// Package attachment builds oba kalkan attachment: list and add the evidence files of a Kalkan
// page. It has no delete: the files of a Kalkan page are kept as a record, and the API refuses
// every delete of one.
package attachment

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const attachmentHelp = `Lists and adds the attachments of the Kalkan page at --page, such as the evidence files of an
ISMS page. Listing them needs the VIEW level on the page's root; adding them, EDIT. An attachment
is kept as a record of the page and cannot be deleted.

A page is named by its path of slugs from its root page, such as engineering/plans; surrounding
slashes are dropped.`

// pageFlag is the --page flag every subcommand takes.
type pageFlag struct {
	path string
}

func (f *pageFlag) register(cmd *cobra.Command) {
	cmd.Flags().StringVar(&f.path, "page", "", "the path of the page that holds the attachments, such as engineering/plans (required)")
	_ = cmd.MarkFlagRequired("page")
}

// normalized checks the path before any call, so that a typo fails without a request.
func (f *pageFlag) normalized() (string, error) {
	return client.NormalizeKalkanPagePath(f.path)
}

// NewCmd builds oba kalkan attachment with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "attachment",
		Short: "List and add the attachments of a Kalkan page",
		Long:  attachmentHelp,
	}
	cmd.AddCommand(newListCmd(), newAddCmd())
	return cmd
}
