// Package attachment builds oba kalkan attachment: list, add and delete the evidence files of a
// Kalkan page.
package attachment

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const attachmentHelp = `Lists, adds and deletes the attachments of the Kalkan page at --page, such as the evidence files
of an ISMS page. Reading them needs the VIEW level on the page's root; changing them, EDIT.

A page is named by its path of slugs from its root page, such as engineering/plans; surrounding
slashes are dropped.`

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

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
		Short: "List, add and delete the attachments of a Kalkan page",
		Long:  attachmentHelp,
	}
	cmd.AddCommand(newListCmd(), newAddCmd(), newDeleteCmd())
	return cmd
}
