package shrine

import "github.com/spf13/cobra"

const documentTemplateHelp = `A document template is reusable content of the organization: an .html file with the PDF
rendered from it, of type PROPOSAL, NDA, TERMS, CONTRACT, REPORT, SERVICE_SHEET, PRESENTATION or
OTHER, from which documents start ('oba document create --template'). A template bound to a
legal entity serves that legal entity only; one without serves every legal entity. Documents
made from a template keep their own copy when the template changes. A mail is no document
template: use 'oba otak mail-template'.

Reading needs the MEMBER role; create and upload need ADMIN. Details other than the content
(name, type, language, legal entity) are edited in the Otak web app.`

// newDocumentTemplateCmd builds oba document template with its list, create, download and upload
// subcommands.
func newDocumentTemplateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "template",
		Short: "List, create, download and upload the document templates of the organization",
		Long:  documentTemplateHelp,
	}
	cmd.AddCommand(newDocumentTemplateListCmd(), newDocumentTemplateCreateCmd(), newDocumentTemplateDownloadCmd(),
		newDocumentTemplateUploadCmd())
	return cmd
}
