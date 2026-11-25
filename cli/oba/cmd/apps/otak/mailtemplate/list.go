package mailtemplate

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/mailtemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const mailTemplateListHelp = `Lists the mail templates of the current organization in one table: NAME, LANGUAGE,
SUBJECT, UPDATED, ID. The ids are what 'oba otak outreach create --type mail --template' takes.

--lang narrows to one language: nl, tr, en, or us for English. --search keeps the templates
whose name contains the text, case ignored.

  oba otak mail-template list --lang nl --search intro`

func newListCmd() *cobra.Command {
	var query mailtemplate.ListQuery
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the mail templates",
		Long:  mailTemplateListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runList(cmd, query)
		},
	}
	cmd.Flags().StringVar(&query.Language, "lang", "", "only mail templates in this language: nl, tr, en or us")
	cmd.Flags().StringVar(&query.Search, "search", "", "only mail templates whose name contains this text")
	return cmd
}

func runList(cmd *cobra.Command, query mailtemplate.ListQuery) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	templates, err := mailtemplate.List(c, query)
	if err != nil {
		return err
	}
	return mailtemplate.PrintList(cmd.OutOrStdout(), templates)
}
