// Package template builds oba otak template: list, push and pull the organization's document
// templates as .html files with their PDF.
package template

import (
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/template"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	templateListHelp = `Lists the document templates of the current organization in one table: NAME, TYPE,
LANGUAGE, READY, UPDATED, ID. READY is "no" for a template whose HTML and PDF are not uploaded
yet; it cannot be used until they are. Mails are mail templates: 'oba otak mail-template list'.

--type narrows to one type (PROPOSAL, NDA, TERMS, REPORT, SERVICE_SHEET, PRESENTATION, OTHER;
case ignored, service-sheet works too). --lang narrows to one language: nl, tr, en, or us for
English.

  oba otak template list --type nda --lang nl`
	templatePushHelp = `Creates a document template from an .html file, or with --id updates one.

Its local images (<img src="../images/logo.png">) are inlined as data URIs, so that the
uploaded HTML renders the same anywhere, and the result is rendered to PDF with WeasyPrint
('brew install weasyprint'). The HTML and the PDF are uploaded together. Everything that can
fail locally fails before anything is sent. A .md mail is a mail template: use
'oba otak mail-template push'.

Without --id, --type and --lang are required and the name is the file name without its
extension unless --name gives one. With --id, --type, --lang and --name default to the stored
values; a given --type changes the template's type. A template's name is unique per language
in the organization.

--lang is nl, tr, en, or us (English).

  oba otak template push legal/nda/nda_nl.html --type nda --lang nl --name "NDA"
  oba otak template push legal/nda/nda_nl.html --id <templateId>`
	templatePullHelp = `Writes a document template into the directory given with -o (default the current one) as
<name>.html and <name>.pdf. An existing file is not overwritten unless --force is given.`
)

// NewCmd builds oba otak template with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "template",
		Short: "List, push and pull the document templates of the organization",
	}

	var query template.ListQuery
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the document templates",
		Long:  templateListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runTemplateList(cmd, query)
		},
	}
	listCmd.Flags().StringVar(&query.Type, "type", "", "only templates of this type")
	listCmd.Flags().StringVar(&query.Language, "lang", "", "only templates in this language: nl, tr, en or us")

	var push template.PushInput
	pushCmd := &cobra.Command{
		Use:   "push <file>",
		Short: "Create or update a document template from an .html file",
		Long:  templatePushHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			in := push
			in.File = args[0]
			in.ID = strings.TrimSpace(in.ID)
			return runTemplatePush(cmd, in)
		},
	}
	pushCmd.Flags().StringVar(&push.Type, "type", "", "the template type: PROPOSAL, NDA, TERMS, REPORT, SERVICE_SHEET, PRESENTATION or OTHER")
	pushCmd.Flags().StringVar(&push.Language, "lang", "", "the language: nl, tr, en or us (English)")
	pushCmd.Flags().StringVar(&push.Name, "name", "", "the template name (default: the file name without extension, or the stored name)")
	pushCmd.Flags().StringVar(&push.ID, "id", "", "update this template instead of creating one")

	var pullDir string
	var pullForce bool
	pullCmd := &cobra.Command{
		Use:   "pull <templateId>",
		Short: "Write a template to files",
		Long:  templatePullHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runTemplatePull(cmd, strings.TrimSpace(args[0]), pullDir, pullForce)
		},
	}
	pullCmd.Flags().StringVarP(&pullDir, "output", "o", ".", "the directory to write into")
	pullCmd.Flags().BoolVar(&pullForce, "force", false, "overwrite existing files")

	cmd.AddCommand(listCmd, pushCmd, pullCmd)
	return cmd
}

func runTemplateList(cmd *cobra.Command, query template.ListQuery) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	templates, err := template.List(c, query)
	if err != nil {
		return err
	}
	return template.PrintList(cmd.OutOrStdout(), templates)
}

func runTemplatePush(cmd *cobra.Command, in template.PushInput) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	result, err := template.Push(cmd.Context(), c, in)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	verb := "Updated"
	if result.Created {
		verb = "Created"
	}
	t := result.Template
	_, _ = fmt.Fprintf(out, "%s template %s (%s, %s, %s)\n", verb, t.ID, t.Name, t.Type, t.Language)
	if result.Uploaded {
		_, _ = fmt.Fprintln(out, "Uploaded the HTML and its PDF.")
	}
	return nil
}

func runTemplatePull(cmd *cobra.Command, id, dir string, force bool) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	paths, err := template.Pull(c, id, dir, force)
	printWritten(cmd.OutOrStdout(), paths)
	return err
}

func printWritten(out io.Writer, paths []string) {
	for _, p := range paths {
		_, _ = fmt.Fprintf(out, "Wrote %s\n", p)
	}
}
