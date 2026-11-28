// Package attachment builds oba otak attachment: list, add, download and delete the attachments
// of the mail of an outreach, of a relation or of a campaign content.
package attachment

import (
	"bufio"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/otak/attachment"
	storage "github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	attachmentHelp = `Lists, adds, downloads and deletes the attachments of the mail of a MAIL outreach (--outreach),
of a relation (--relation) or of a campaign content (--content, its images); pass exactly one of
the three. The attachments of a mail go along when the outreach is sent from the Otak web app;
once it is SENT they can no longer change (OTK_STA_011). Changing the attachments of a mail needs
the ADMIN role; a relation's or a content's, the MEMBER role.`
	attachmentListHelp = `Lists the attachments in upload order: NAME, SIZE (bytes), TYPE, CREATED, ID.

  oba otak attachment list --outreach <outreachId>
  oba otak attachment list --relation <relationId>
  oba otak attachment list --content <contentId>`
	attachmentAddHelp = `Adds local files as attachments: --file <path>, repeated for more files, sent in one upload of
at most 12 MB together. The same content twice on one parent is refused (STG_VAL_005).
A document's PDF goes onto a mail with 'oba document attach-to-mail'.

  oba otak attachment add --outreach <outreachId> --file voorstel.pdf --file prijzen.xlsx`
	attachmentDeleteHelp = `Deletes one attachment after asking for confirmation; without a terminal to ask on, pass
--yes. The id must be an attachment of the given outreach, relation or content.

  oba otak attachment delete --outreach <outreachId> --id <attachmentId>`
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// targetFlags are the --outreach, --relation and --content flags every subcommand takes.
type targetFlags struct {
	outreach string
	relation string
	content  string
}

func (f *targetFlags) register(cmd *cobra.Command) {
	cmd.Flags().StringVar(&f.outreach, "outreach", "", "the MAIL outreach whose mail holds the attachments")
	cmd.Flags().StringVar(&f.relation, "relation", "", "the relation that holds the attachments")
	cmd.Flags().StringVar(&f.content, "content", "", "the campaign content that holds the attachments, its images")
}

func (f *targetFlags) resolve(c *client.Client) (attachment.Target, error) {
	return attachment.ResolveTarget(c, strings.TrimSpace(f.outreach), strings.TrimSpace(f.relation),
		strings.TrimSpace(f.content))
}

// NewCmd builds oba otak attachment with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "attachment",
		Short: "List, add, download and delete the attachments of an outreach's mail, a relation or a campaign content",
		Long:  attachmentHelp,
	}

	var listTarget targetFlags
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the attachments of an outreach's mail, a relation or a campaign content",
		Long:  attachmentListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runAttachmentList(cmd, &listTarget)
		},
	}
	listTarget.register(listCmd)

	var addTarget targetFlags
	var addFiles []string
	addCmd := &cobra.Command{
		Use:   "add",
		Short: "Add local files as attachments",
		Long:  attachmentAddHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runAttachmentAdd(cmd, &addTarget, addFiles)
		},
	}
	addTarget.register(addCmd)
	addCmd.Flags().StringArrayVar(&addFiles, "file", nil, "a local file to attach; repeat it for more files (required)")

	var deleteTarget targetFlags
	var deleteID string
	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete",
		Short: "Delete an attachment of an outreach's mail, a relation or a campaign content",
		Long:  attachmentDeleteHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runAttachmentDelete(cmd, &deleteTarget, strings.TrimSpace(deleteID), deleteYes)
		},
	}
	deleteTarget.register(deleteCmd)
	deleteCmd.Flags().StringVar(&deleteID, "id", "", "the attachment id, from 'oba otak attachment list' (required)")
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")
	_ = deleteCmd.MarkFlagRequired("id")

	cmd.AddCommand(listCmd, addCmd, newDownloadCmd(), deleteCmd)
	return cmd
}

func runAttachmentList(cmd *cobra.Command, flags *targetFlags) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	target, err := flags.resolve(c)
	if err != nil {
		return err
	}
	attachments, err := storage.List(c, target.EntityType, target.EntityID)
	if err != nil {
		return err
	}
	return storage.PrintList(cmd.OutOrStdout(), attachments)
}

func runAttachmentAdd(cmd *cobra.Command, flags *targetFlags, paths []string) error {
	if len(paths) == 0 {
		return fmt.Errorf("pass --file with a local file to attach")
	}
	// The files are read and checked before any request, so that a bad path fails fast.
	files, err := attachment.ReadFiles(paths)
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	target, err := flags.resolve(c)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	added, err := storage.Add(c, target.EntityType, target.EntityID, files)
	if err != nil {
		return err
	}
	for _, a := range added {
		_, _ = fmt.Fprintf(out, "Added attachment %s (%s) to %s\n", a.ID, guardrail.OneLine(a.FileName), target.Label)
	}
	return nil
}

func runAttachmentDelete(cmd *cobra.Command, flags *targetFlags, id string, yes bool) error {
	if !yes && !stdinIsTerminal() {
		return terminal.ErrDeleteNeedsYes
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	target, err := flags.resolve(c)
	if err != nil {
		return err
	}
	found, err := attachment.Find(c, target, id)
	if err != nil {
		return err
	}
	name := guardrail.OneLine(found.FileName)
	out := cmd.OutOrStdout()
	if !yes {
		src := cmd.InOrStdin()
		question := fmt.Sprintf("Delete attachment %s of %s?", name, target.Label)
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, question)
		if err != nil {
			return err
		}
		if !confirmed {
			// The lookups before the question are not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := storage.Delete(c, found); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted attachment %s (%s) of %s\n", found.ID, name, target.Label)
	return nil
}
