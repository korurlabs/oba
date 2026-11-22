package shrine

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/documenttemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentTemplateListHelp = `Lists the document templates of the organization in one table: NAME, TYPE, LANGUAGE, LEGAL
ENTITY, READY, UPDATED, ID. LEGAL ENTITY is "all" for a template every legal entity may use;
READY is "no" for a template whose HTML and PDF are not uploaded yet, which cannot be used until
they are.

Every filter is optional: --type (case ignored, service-sheet works too), --language (nl, tr,
en, or us for English), --legal-entity (its own templates and those of every legal entity) and
--search (part of the name, case ignored).

  oba document template list --type nda --language nl`

func newDocumentTemplateListCmd() *cobra.Command {
	var query documenttemplate.ListQuery
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the document templates",
		Long:  documentTemplateListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := query.Check(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			templates, err := documenttemplate.List(c, query)
			if err != nil {
				return err
			}
			return documenttemplate.PrintList(cmd.OutOrStdout(), templates)
		},
	}
	fl := cmd.Flags()
	fl.StringVar(&query.Type, "type", "", "only templates of this type")
	fl.StringVar(&query.Language, "language", "", "only templates in this language: nl, tr, en or us")
	fl.StringVar(&query.LegalEntityID, "legal-entity", "", "only templates this legal entity may use")
	fl.StringVar(&query.Search, "search", "", "only templates whose name contains this text")
	return cmd
}
