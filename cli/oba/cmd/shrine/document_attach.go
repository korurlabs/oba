package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/shrine/document"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentAttachHelp = `Stores a copy of the document's current PDF on a mail, as the attachment <title>.pdf. The mail
must belong to the document's relation and not be sent yet; the copy stays as it is when the
document changes later. --mail is the mail id that 'oba otak outreach create' prints. It needs
the ADMIN role.

  oba document attach-to-mail <documentId> --mail <mailId>`

func newDocumentAttachCmd() *cobra.Command {
	var mailID string
	cmd := &cobra.Command{
		Use:   "attach-to-mail <id>",
		Short: "Attach a copy of a document's PDF to a mail",
		Long:  documentAttachHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := document.ParseID(args[0])
			if err != nil {
				return err
			}
			mail, err := document.ParseMailID(mailID)
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			a, err := document.AttachToMail(c, id, mail)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Attached %s (attachment %s) to mail %s\n",
				guardrail.OneLine(a.FileName), a.ID, a.EntityID)
			return nil
		},
	}
	cmd.Flags().StringVar(&mailID, "mail", "", "the mail to attach the PDF to (required)")
	return cmd
}
