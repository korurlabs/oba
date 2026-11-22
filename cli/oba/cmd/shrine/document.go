package shrine

import "github.com/spf13/cobra"

const documentHelp = `A document is an HTML file with its PDF that belongs to an Otak relation and is written in the
name of one of the organization's legal entities ('oba legal-entity list'): a proposal, an NDA,
a contract. It starts from a document template ('oba document template list') or a local .html
file. Its PDF is rendered locally with WeasyPrint on every upload ('brew install weasyprint'),
and attaching it to a mail stores a copy of that PDF on the mail, which later uploads leave as
it is.

The commands act in the current organization ('oba org use'), or the one --org names. They
need the MEMBER role and access to Otak; attaching to a mail needs the ADMIN role.`

// newDocumentCmd builds oba document with its list, create, download, upload and attach-to-mail
// subcommands and the template group.
func newDocumentCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "document",
		Short: "List, create, download, upload and attach the documents of the organization",
		Long:  documentHelp,
	}
	cmd.AddCommand(newDocumentListCmd(), newDocumentCreateCmd(), newDocumentDownloadCmd(),
		newDocumentUploadCmd(), newDocumentAttachCmd(), newDocumentTemplateCmd())
	return cmd
}
