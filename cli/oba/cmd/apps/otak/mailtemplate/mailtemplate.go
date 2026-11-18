// Package mailtemplate builds oba otak mail-template: list, push and pull the organization's mail
// templates as .md files.
package mailtemplate

import (
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/mailtemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	mailTemplateListHelp = `Lists the mail templates of the current organization in one table: NAME, LANGUAGE,
SUBJECT, UPDATED, ID. The ids are what 'oba otak outreach create --type mail --template' takes.

--lang narrows to one language: nl, tr, en, or us for English. --search keeps the templates
whose name contains the text, case ignored.

  oba otak mail-template list --lang nl --search intro`
	mailTemplatePushHelp = `Creates a mail template from a .md file, or with --id replaces one.

The file holds the subject on the first line, a blank line, then the body as plain text. The
blank lines after the subject and the trailing white space of the body are dropped.

Without --id, --lang is required and the name is the file name without its extension unless
--name gives one. With --id, --lang and --name default to the stored values, and subject and
body come from the file. A mail template's name is unique per language in the organization.
Outreaches already copied from a mail template keep their own mail when it changes.

--lang is nl, tr, en, or us (English).

  oba otak mail-template push mails/first-contact.md --lang en
  oba otak mail-template push mails/first-contact.md --id <mailTemplateId>`
	mailTemplatePullHelp = `Writes a mail template into the directory given with -o (default the current one) as
<name>.md, in the form 'push' reads. An existing file is not overwritten unless --force is
given.`
)

// NewCmd builds oba otak mail-template with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "mail-template",
		Short: "List, push and pull the mail templates of the organization",
	}

	var query mailtemplate.ListQuery
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the mail templates",
		Long:  mailTemplateListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runList(cmd, query)
		},
	}
	listCmd.Flags().StringVar(&query.Language, "lang", "", "only mail templates in this language: nl, tr, en or us")
	listCmd.Flags().StringVar(&query.Search, "search", "", "only mail templates whose name contains this text")

	var push mailtemplate.PushInput
	pushCmd := &cobra.Command{
		Use:   "push <file.md>",
		Short: "Create or update a mail template from a .md file",
		Long:  mailTemplatePushHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			in := push
			in.File = args[0]
			in.ID = strings.TrimSpace(in.ID)
			return runPush(cmd, in)
		},
	}
	pushCmd.Flags().StringVar(&push.Language, "lang", "", "the language: nl, tr, en or us (English)")
	pushCmd.Flags().StringVar(&push.Name, "name", "", "the mail template name (default: the file name without extension, or the stored name)")
	pushCmd.Flags().StringVar(&push.ID, "id", "", "update this mail template instead of creating one")

	var pullDir string
	var pullForce bool
	pullCmd := &cobra.Command{
		Use:   "pull <mailTemplateId>",
		Short: "Write a mail template to a .md file",
		Long:  mailTemplatePullHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runPull(cmd, strings.TrimSpace(args[0]), pullDir, pullForce)
		},
	}
	pullCmd.Flags().StringVarP(&pullDir, "output", "o", ".", "the directory to write into")
	pullCmd.Flags().BoolVar(&pullForce, "force", false, "overwrite an existing file")

	cmd.AddCommand(listCmd, pushCmd, pullCmd)
	return cmd
}

func runList(cmd *cobra.Command, query mailtemplate.ListQuery) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	templates, err := mailtemplate.List(c, query)
	if err != nil {
		return err
	}
	return mailtemplate.PrintList(cmd.OutOrStdout(), templates)
}

func runPush(cmd *cobra.Command, in mailtemplate.PushInput) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	result, err := mailtemplate.Push(c, in)
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

func runPull(cmd *cobra.Command, id, dir string, force bool) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	paths, err := mailtemplate.Pull(c, id, dir, force)
	printWritten(cmd.OutOrStdout(), paths)
	return err
}

func printWritten(out io.Writer, paths []string) {
	for _, p := range paths {
		_, _ = fmt.Fprintf(out, "Wrote %s\n", p)
	}
}
