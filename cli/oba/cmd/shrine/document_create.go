package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/document"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentCreateHelp = `Creates a document of a relation from exactly one of:
  --template <id>       a document template ('oba document template list'); the API copies its
                        HTML and PDF to the new document
  --file <file.html>    a local .html file; its local images are inlined as data URIs, it is
                        rendered to PDF with WeasyPrint, and both are uploaded after the create

--relation, --legal-entity, --title, --type and --language are required. --type is PROPOSAL,
NDA, TERMS, CONTRACT, REPORT, SERVICE_SHEET, PRESENTATION or OTHER; --language is nl, tr, en,
or us (English). A file is read and rendered before anything is sent. A template bound to
another legal entity is refused (DOC_VAL_002).

A document is never created again by a retry: to change its content later, use
'oba document upload <id> <file.html>'.

  oba document create --relation <relationId> --legal-entity <legalEntityId> --title "NDA Acme" \
    --type nda --language nl --template <templateId>
  oba document create --relation <relationId> --legal-entity <legalEntityId> --title "Proposal Acme" \
    --type proposal --language en --file proposal.html`

func newDocumentCreateCmd() *cobra.Command {
	var in document.CreateInput
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a document from a template or an .html file",
		Long:  documentCreateHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := in.Check(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			result, err := document.Create(cmd.Context(), c, in)
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			d := result.Document
			_, _ = fmt.Fprintf(out, "Created document %s (%s, %s)\n", document.Label(d), d.Type, d.Language)
			switch {
			case result.Uploaded:
				_, _ = fmt.Fprintln(out, "Uploaded the HTML and its PDF.")
			case d.Ready:
				_, _ = fmt.Fprintln(out, "Copied the HTML and the PDF of the template.")
			default:
				_, _ = fmt.Fprintf(out, "The template has no files yet, so neither has the document; upload its HTML with "+
					"'oba document upload %s <file.html>'.\n", d.ID)
			}
			return nil
		},
	}
	fl := cmd.Flags()
	fl.StringVar(&in.RelationID, "relation", "", "the relation the document belongs to (required)")
	fl.StringVar(&in.LegalEntityID, "legal-entity", "", "the legal entity the document is written in the name of (required)")
	fl.StringVar(&in.Title, "title", "", "the document title, max 200 (required)")
	fl.StringVar(&in.Type, "type", "", "PROPOSAL, NDA, TERMS, CONTRACT, REPORT, SERVICE_SHEET, PRESENTATION or OTHER (required)")
	fl.StringVar(&in.Language, "language", "", "nl, tr, en or us (English) (required)")
	fl.StringVar(&in.TemplateID, "template", "", "the document template to copy the files from")
	fl.StringVar(&in.File, "file", "", "the local .html file to render and upload")
	return cmd
}
