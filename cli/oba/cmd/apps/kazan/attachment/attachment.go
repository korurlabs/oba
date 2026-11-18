// Package attachment builds the oba kazan attachment commands: list, add and delete the files
// attached to a ticket.
package attachment

import (
	"bufio"
	"errors"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	ticketHelp = "The ticket is its key, such as OPS-12, or its id. A key names its own board; an id is " +
		"looked up on --board, else on the default board."
	attachmentAddHelp = `Attaches local files to a ticket in one upload: repeat --file for more files, at most 12 MB
together. Content already attached to any ticket of the organization is refused, and the file
that holds it is named before anything is uploaded.

  oba kazan attachment add OPS-12 --file screenshot.png --file logs.txt`
	attachmentDeleteHelp = `Deletes one attachment of a ticket. The id must be an attachment of that ticket. On a terminal
it asks for confirmation first, unless --yes is given.

  oba kazan attachment delete OPS-12 --id <attachmentId>`
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan attachment command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "attachment",
		Short: "List, add and delete the files attached to a ticket",
		Long:  "Lists, adds and deletes the files attached to a Kazan ticket.\n\n" + ticketHelp,
	}

	listCmd := &cobra.Command{
		Use:   "list <ticket>",
		Short: "List the attachments of a ticket",
		Long:  "Lists the attachments of a ticket in upload order: NAME, SIZE (bytes), TYPE, CREATED, ID.\n\n" + ticketHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runList,
	}

	var files []string
	addCmd := &cobra.Command{
		Use:   "add <ticket>",
		Short: "Attach files to a ticket",
		Long:  attachmentAddHelp + "\n\n" + ticketHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runAdd(cmd, args[0], files)
		},
	}
	addCmd.Flags().StringArrayVar(&files, "file", nil, "a file to attach; repeat it for more files")

	var deleteID string
	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <ticket>",
		Short: "Delete an attachment of a ticket",
		Long:  attachmentDeleteHelp + "\n\n" + ticketHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args[0], strings.TrimSpace(deleteID), deleteYes)
		},
	}
	deleteCmd.Flags().StringVar(&deleteID, "id", "", "the attachment id, from 'oba kazan attachment list' (required)")
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, addCmd, deleteCmd)
	return cmd
}

// signIn returns the organization client and the ticket ref names.
func signIn(cmd *cobra.Command, ref string) (*client.Client, ticket.Target, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, ticket.Target{}, err
	}
	t, err := ticket.Resolve(c, ref, boardflag.Value(cmd), cmd.ErrOrStderr())
	if err != nil {
		return nil, ticket.Target{}, err
	}
	return c, t, nil
}

func runList(cmd *cobra.Command, args []string) error {
	c, t, err := signIn(cmd, args[0])
	if err != nil {
		return err
	}
	attachments, err := attachment.List(c, ticket.AttachmentEntityType, t.ID)
	if err != nil {
		return err
	}
	return attachment.PrintList(cmd.OutOrStdout(), attachments)
}

func runAdd(cmd *cobra.Command, ref string, paths []string) error {
	// The files are read and checked before any request, so that a bad path fails fast.
	files, err := attachment.ReadFiles(paths)
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return errors.New("--file is required: the file to attach (repeat the flag for more files)")
	}
	c, t, err := signIn(cmd, ref)
	if err != nil {
		return err
	}
	if err := ticket.CheckAttachmentDuplicates(c, files, cmd.ErrOrStderr()); err != nil {
		return err
	}
	added, err := attachment.Add(c, ticket.AttachmentEntityType, t.ID, files)
	if err != nil {
		return err
	}
	names := make([]string, 0, len(added))
	for _, a := range added {
		names = append(names, guardrail.OneLine(a.FileName))
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Attached %d file(s) to %s: %s\n", len(added), t.Label(), strings.Join(names, ", "))
	return nil
}

func runDelete(cmd *cobra.Command, ref, attachmentID string, yes bool) error {
	if attachmentID == "" {
		return errors.New("--id is required: the attachment id from 'oba kazan attachment list'")
	}
	c, t, err := signIn(cmd, ref)
	if err != nil {
		return err
	}
	found, err := ticket.FindAttachment(c, t.ID, attachmentID)
	if err != nil {
		return err
	}
	name := guardrail.OneLine(found.FileName)
	out := cmd.OutOrStdout()
	if !yes && stdinIsTerminal() {
		src := cmd.InOrStdin()
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete attachment '%s'?", name))
		if err != nil {
			return err
		}
		if !confirmed {
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := attachment.Delete(c, found); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted attachment %s\n", name)
	return nil
}
