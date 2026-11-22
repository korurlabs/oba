package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/documenttemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentTemplateUploadHelp = `Replaces the HTML and the PDF of a document template with an .html file and the PDF rendered
from it. Its local images are inlined as data URIs and it is rendered with WeasyPrint before
anything is sent. Documents already made from the template keep their own files.

  oba document template upload <templateId> legal/nda/nda_nl.html`

func newDocumentTemplateUploadCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "upload <id> <file.html>",
		Short: "Replace the HTML and the PDF of a document template",
		Long:  documentTemplateUploadHelp,
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := documenttemplate.ParseID(args[0])
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			t, err := documenttemplate.Upload(cmd.Context(), c, id, args[1])
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Uploaded the HTML and its PDF to document template %s\n", documenttemplate.Label(t))
			return nil
		},
	}
}
