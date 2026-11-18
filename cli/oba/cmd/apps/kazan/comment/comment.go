// Package comment builds the oba kazan comment commands: list, add, change and delete the
// comments of a ticket.
package comment

import (
	"bufio"
	"errors"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/comment"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	ticketHelp = "The ticket is its key, such as OPS-12, or its id. A key names its own board; an id is " +
		"looked up on --board, else on the default board."
	commentPickHelp = "Without --comment-id on a terminal, the ticket's comments are listed and you pick one by number."
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan comment command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "comment",
		Short: "List, add, change and delete the comments of a ticket",
		Long:  "Lists, adds, changes and deletes the comments of a Kazan ticket.\n\n" + ticketHelp,
	}

	listCmd := &cobra.Command{
		Use:   "list <ticket>",
		Short: "List the comments of a ticket",
		Long:  "Lists the comments of a ticket: ID, AUTHOR, DATE and the start of the CONTENT.\n\n" + ticketHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runList,
	}

	var addContent string
	addCmd := &cobra.Command{
		Use:   "add <ticket>",
		Short: "Add a comment to a ticket",
		Long: "Adds a comment with the text of --content; on a terminal without --content it asks for the text.\n\n" +
			ticketHelp + "\n\n  oba kazan comment add OPS-12 --content \"Deployed to staging.\"",
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runAdd(cmd, args[0], addContent)
		},
	}
	addCmd.Flags().StringVar(&addContent, "content", "", "the comment text")

	var updateID, updateContent string
	updateCmd := &cobra.Command{
		Use:   "update <ticket>",
		Short: "Change the text of a comment",
		Long: "Replaces the text of comment --comment-id with --content; on a terminal without --content it asks " +
			"for the new text.\n\n" + commentPickHelp + "\n\n" + ticketHelp,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runUpdate(cmd, args[0], strings.TrimSpace(updateID), updateContent)
		},
	}
	updateCmd.Flags().StringVar(&updateID, "comment-id", "", "the comment to change, from 'oba kazan comment list'")
	updateCmd.Flags().StringVar(&updateContent, "content", "", "the new comment text")

	var deleteID string
	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <ticket>",
		Short: "Delete a comment",
		Long: "Deletes comment --comment-id. On a terminal it asks for confirmation first, unless --yes is given.\n\n" +
			commentPickHelp + "\n\n" + ticketHelp,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args[0], strings.TrimSpace(deleteID), deleteYes)
		},
	}
	deleteCmd.Flags().StringVar(&deleteID, "comment-id", "", "the comment to delete, from 'oba kazan comment list'")
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, addCmd, updateCmd, deleteCmd)
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
	comments, err := comment.List(c, t)
	if err != nil {
		return err
	}
	return comment.PrintList(cmd.OutOrStdout(), comments)
}

func runAdd(cmd *cobra.Command, ref, content string) error {
	c, t, err := signIn(cmd, ref)
	if err != nil {
		return err
	}
	in := bufio.NewReader(cmd.InOrStdin())
	content, err = contentOrAsk(cmd, in, content, "Comment: ")
	if err != nil {
		return err
	}
	added, err := comment.Add(c, t, content)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Added comment %s\n", added.ID)
	return nil
}

func runUpdate(cmd *cobra.Command, ref, commentID, content string) error {
	c, t, err := signIn(cmd, ref)
	if err != nil {
		return err
	}
	in := bufio.NewReader(cmd.InOrStdin())
	commentID, done, err := commentIDOrPick(cmd, c, t, in, commentID)
	if err != nil || done {
		return err
	}
	content, err = contentOrAsk(cmd, in, content, "New content: ")
	if err != nil {
		return err
	}
	if _, err := comment.Update(c, t, commentID, content); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated comment %s\n", commentID)
	return nil
}

func runDelete(cmd *cobra.Command, ref, commentID string, yes bool) error {
	c, t, err := signIn(cmd, ref)
	if err != nil {
		return err
	}
	in := bufio.NewReader(cmd.InOrStdin())
	commentID, done, err := commentIDOrPick(cmd, c, t, in, commentID)
	if err != nil || done {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes && stdinIsTerminal() {
		confirmed, err := terminal.Confirm(in, cmd.InOrStdin(), out, "Delete this comment?")
		if err != nil {
			return err
		}
		if !confirmed {
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := comment.Delete(c, t, commentID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted comment %s\n", commentID)
	return nil
}

// commentIDOrPick returns commentID, or on a terminal without it the comment the user picks.
// done reports that the run ends here without an error: the user left the pick list or the
// ticket has no comments, and the result is then null.
func commentIDOrPick(cmd *cobra.Command, c *client.Client, t ticket.Target, in *bufio.Reader, commentID string) (string, bool, error) {
	if commentID != "" {
		return commentID, false, nil
	}
	if !stdinIsTerminal() {
		return "", false, errors.New("--comment-id is required without a terminal; 'oba kazan comment list' lists the ids")
	}
	out := cmd.OutOrStdout()
	picked, err := comment.Pick(c, t, in, cmd.InOrStdin(), out)
	switch {
	case errors.Is(err, comment.ErrCancelled):
		jsonresult.Nothing()
		return "", true, nil
	case errors.Is(err, comment.ErrNoComments):
		jsonresult.Nothing()
		_, _ = fmt.Fprintln(out, err.Error())
		return "", true, nil
	case err != nil:
		return "", false, err
	}
	return picked, false, nil
}

// contentOrAsk returns content, or on a terminal without it the text the user types at prompt,
// asked again until it is not empty.
func contentOrAsk(cmd *cobra.Command, in *bufio.Reader, content, prompt string) (string, error) {
	if strings.TrimSpace(content) != "" {
		return content, nil
	}
	if !stdinIsTerminal() {
		return "", errors.New("--content is required")
	}
	for {
		answer, eof, err := terminal.Ask(in, cmd.InOrStdin(), cmd.OutOrStdout(), prompt)
		if err != nil {
			return "", err
		}
		if answer != "" {
			return answer, nil
		}
		if eof {
			return "", errors.New("--content is required")
		}
	}
}
