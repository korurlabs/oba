package page

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kalkan/page"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const pageGetHelp = `Prints the page at <path>: a header with its version, your level and the compliance mark, then
every translation under a header of its own. With --lang, prints only the Markdown body in that
language, ready to redirect to a file. With --json, prints the page with every translation as the
API sent it.

  oba kalkan page get engineering/plans
  oba kalkan page get engineering/plans --lang tr > plans.tr.md`

func newGetCmd() *cobra.Command {
	var lang string
	cmd := &cobra.Command{
		Use:   "get <path>",
		Short: "Print a page with its translations, or one body",
		Long:  pageGetHelp + "\n\n" + pagePathHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runGet(cmd, args[0], lang)
		},
	}
	cmd.Flags().StringVar(&lang, "lang", "", "print only the body in this language: tr or en")
	return cmd
}

func runGet(cmd *cobra.Command, rawPath, lang string) error {
	path, err := client.NormalizeKalkanPagePath(rawPath)
	if err != nil {
		return err
	}
	locale := ""
	if lang != "" {
		if locale, err = client.KalkanLocale(lang); err != nil {
			return err
		}
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	detail, err := page.Get(c, path)
	if err != nil {
		return err
	}
	if locale != "" {
		return page.PrintBody(cmd.OutOrStdout(), detail, locale)
	}
	page.PrintPage(cmd.OutOrStdout(), detail)
	return nil
}
