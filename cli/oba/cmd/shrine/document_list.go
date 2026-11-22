package shrine

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/document"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentListHelp = `Lists the documents of the organization, last changed first: TITLE, TYPE, LANGUAGE, RELATION,
LEGAL ENTITY, READY, SENT, UPDATED, ID. READY is "no" for a document whose HTML and PDF are not
uploaded yet; SENT counts the mails it was attached to.

Every filter is optional: --relation, --legal-entity, --type (PROPOSAL, NDA, TERMS, CONTRACT,
REPORT, SERVICE_SHEET, PRESENTATION, OTHER; case ignored, service-sheet works too), --language
(nl, tr, en, or us for English) and --search (part of the title, case ignored).

  oba document list --relation <relationId>
  oba document list --type contract --language nl`

func newDocumentListCmd() *cobra.Command {
	var query document.ListQuery
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the documents",
		Long:  documentListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := query.Check(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			documents, err := document.List(c, query)
			if err != nil {
				return err
			}
			return document.PrintList(cmd.OutOrStdout(), documents)
		},
	}
	fl := cmd.Flags()
	fl.StringVar(&query.RelationID, "relation", "", "only the documents of this relation")
	fl.StringVar(&query.LegalEntityID, "legal-entity", "", "only the documents of this legal entity")
	fl.StringVar(&query.Type, "type", "", "only documents of this type")
	fl.StringVar(&query.Language, "language", "", "only documents in this language: nl, tr, en or us")
	fl.StringVar(&query.Search, "search", "", "only documents whose title contains this text")
	return cmd
}
