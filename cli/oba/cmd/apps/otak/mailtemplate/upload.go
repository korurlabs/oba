package mailtemplate

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/mailtemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const mailTemplateUploadHelp = `Creates a mail template from a .md file, or with --id replaces one.

The file holds the subject on the first line, a blank line, then the body as plain text. The
blank lines after the subject and the trailing white space of the body are dropped.

Without --id, --lang is required and the name is the file name without its extension unless
--name gives one. With --id, --lang and --name default to the stored values, and subject and
body come from the file. A mail template's name is unique per language in the organization.
Outreaches already copied from a mail template keep their own mail when it changes.

--lang is nl, tr, en, or us (English).

  oba otak mail-template upload mails/first-contact.md --lang en
  oba otak mail-template upload mails/first-contact.md --id <mailTemplateId>`

func newUploadCmd() *cobra.Command {
	var upload mailtemplate.UploadInput
	cmd := &cobra.Command{
		Use:   "upload <file.md>",
		Short: "Create or update a mail template from a .md file",
		Long:  mailTemplateUploadHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			in := upload
			in.File = args[0]
			in.ID = strings.TrimSpace(in.ID)
			return runUpload(cmd, in)
		},
	}
	cmd.Flags().StringVar(&upload.Language, "lang", "", "the language: nl, tr, en or us (English)")
	cmd.Flags().StringVar(&upload.Name, "name", "", "the mail template name (default: the file name without extension, or the stored name)")
	cmd.Flags().StringVar(&upload.ID, "id", "", "update this mail template instead of creating one")
	return cmd
}

func runUpload(cmd *cobra.Command, in mailtemplate.UploadInput) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	result, err := mailtemplate.Upload(c, in)
	if err != nil {
		return err
	}
	verb := "Updated"
	if result.Created {
		verb = "Created"
	}
	t := result.Template
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "%s mail template %s (%s, %s)\n", verb, t.ID, t.Name, t.Language)
	return nil
}
