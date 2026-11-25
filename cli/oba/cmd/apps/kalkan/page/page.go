// Package page builds oba kalkan page: list the tree of pages, and read, write and delete one
// page by its path.
package page

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const pagePathHelp = "A page is named by its path of slugs from its root page, such as engineering/plans; " +
	"surrounding slashes are dropped. A slug starts with a letter or digit, then up to 99 letters, digits, " +
	"'.', '_' or '-', and a path is at most 10 slugs deep."

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds oba kalkan page with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "page",
		Short: "List, read, write and delete Kalkan pages",
		Long: "Lists the pages you can see as a tree, and reads, writes and deletes one page by its path.\n\n" +
			pagePathHelp,
	}
	cmd.AddCommand(newListCmd(), newGetCmd(), newPutCmd(), newDeleteCmd())
	return cmd
}
