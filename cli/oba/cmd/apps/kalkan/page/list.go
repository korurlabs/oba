package page

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kalkan/page"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const pageListHelp = `Lists every page under the root pages you can see, as a tree: the path, the title (English,
else Turkish), [compliance] on a compliance document and, on a root page, your level in
parentheses. With a path, lists that page and the pages below it; a path you cannot see exits 2.
With --json, prints the pages as a flat array, parents first.

  oba kalkan page list
  oba kalkan page list engineering/plans`

func newListCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "list [<path>]",
		Short: "List the pages you can see, or the subtree of one page",
		Long:  pageListHelp + "\n\n" + pagePathHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE:  runList,
	}
}

func runList(cmd *cobra.Command, args []string) error {
	path := ""
	if len(args) == 1 {
		normalized, err := client.NormalizeKalkanPagePath(args[0])
		if err != nil {
			return err
		}
		path = normalized
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	pages, err := page.List(c, path)
	if err != nil {
		return err
	}
	if path != "" {
		// The list call read the whole tree; the result is the subtree.
		if err := jsonresult.SetValue(pages); err != nil {
			return err
		}
	}
	page.PrintTree(cmd.OutOrStdout(), pages)
	return nil
}
