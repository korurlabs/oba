package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/documenttemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentTemplateCreateHelp = `Creates a document template from an .html file. Its local images (<img src="../images/logo.png">)
are inlined as data URIs, so that the uploaded HTML renders the same anywhere, and the result
is rendered to PDF with WeasyPrint ('brew install weasyprint'). The HTML and the PDF are
uploaded together after the create. Everything that can fail locally fails before anything is
sent.

--file, --type and --language are required. The name is the file name without its extension
unless --name gives one; it is unique per language in the organization (DOC_STA_001).
--legal-entity binds the template to one legal entity; without it every legal entity may use it.
To change the content later, use 'oba document template upload <id> <file.html>'.

  oba document template create --file legal/nda/nda_nl.html --type nda --language nl --name "NDA"`

func newDocumentTemplateCreateCmd() *cobra.Command {
	var in documenttemplate.CreateInput
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a document template from an .html file",
		Long:  documentTemplateCreateHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := in.Check(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			t, err := documenttemplate.Create(cmd.Context(), c, in)
			if err != nil {
				return err
			}
			out := cmd.OutOrStdout()
			_, _ = fmt.Fprintf(out, "Created document template %s (%s, %s)\n", documenttemplate.Label(t), t.Type, t.Language)
			_, _ = fmt.Fprintln(out, "Uploaded the HTML and its PDF.")
			return nil
		},
	}
	fl := cmd.Flags()
	fl.StringVar(&in.File, "file", "", "the local .html file to render and upload (required)")
	fl.StringVar(&in.Type, "type", "", "PROPOSAL, NDA, TERMS, CONTRACT, REPORT, SERVICE_SHEET, PRESENTATION or OTHER (required)")
	fl.StringVar(&in.Language, "language", "", "nl, tr, en or us (English) (required)")
	fl.StringVar(&in.Name, "name", "", "the template name, max 100 (default: the file name without extension)")
	fl.StringVar(&in.LegalEntityID, "legal-entity", "", "the only legal entity that may use the template (default: every one)")
	return cmd
}
