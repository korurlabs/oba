package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/document"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentUploadHelp = `Replaces the HTML and the PDF of a document with an .html file and the PDF rendered from it.
Its local images are inlined as data URIs and it is rendered with WeasyPrint before anything is
sent. Mails the document was attached to keep the PDF they got.

  oba document upload <documentId> work/nda-acme.html`

func newDocumentUploadCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "upload <id> <file.html>",
		Short: "Replace the HTML and the PDF of a document",
		Long:  documentUploadHelp,
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := document.ParseID(args[0])
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			d, err := document.Upload(cmd.Context(), c, id, args[1])
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Uploaded the HTML and its PDF to document %s\n", document.Label(d))
			return nil
		},
	}
}
