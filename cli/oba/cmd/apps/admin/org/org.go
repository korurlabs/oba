// Package org builds the oba admin org commands: create an organization, list the ones the
// signed-in user belongs to (the same list as oba org list) and update the profile of the
// current one.
package org

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/shrine"
	"github.com/korurlabs/oba-cli/internal/apps/admin/org"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// orgHelp is the Long text of oba admin org. It names what the group leaves out on purpose, so
// that a person or an agent looking for delete, disable or enable learns where they are done.
const orgHelp = `Create, list and update organizations. Deleting, disabling and enabling an organization are left
out of this CLI on purpose: delete cannot be undone, and both change who can use the
organization. Do them in the Oba admin web app.`

const orgCreateHelp = `Creates one organization from one JSON object on stdin and makes you its owner. The keys are
the API's field names and the values go to the API unchanged; a key the API does not know is
refused before anything is sent. It needs no current organization, so it works before you
belong to any. The country is an ISO 3166-1 alpha-3 code.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba admin org create <<'EOF'
  {"name": "Korur B.V.", "country": "NLD", "website": "https://korur.com/"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba admin org create < org.json

Work in the new organization with 'oba org use <id>'.`

const orgUpdateHelp = `Updates the profile of the current organization (the one 'oba org use' chose) from one JSON
object on stdin. Only the given keys change: the command reads the organization, lays the keys
over its current values and sends every field back, since the API replaces them all. null clears
an optional field; name cannot be cleared. A key the API does not know is refused before
anything is sent, and when every key already has its value nothing is sent. It needs the ADMIN
role in the organization.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba admin org update <<'EOF'
  {"description": "Cybersecurity for SMEs", "website": null}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba admin org update < patch.json`

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds "oba admin org" with its create, list and update commands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "org",
		Short: "Create, list and update organizations",
		Long:  orgHelp,
	}

	createCmd := &cobra.Command{
		Use:   "create",
		Short: "Create an organization from one JSON object on stdin; you become its owner",
		Long:  orgCreateHelp,
		Args:  cobra.NoArgs,
		RunE:  runOrgCreate,
	}
	createCmd.SetHelpFunc(orgFieldHelp(orgCreateHelp, org.CreateFields, true))

	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List your organizations with your role in each; * marks the current one",
		Args:  cobra.NoArgs,
		RunE:  shrine.RunOrgList,
	}

	updateCmd := &cobra.Command{
		Use:   "update",
		Short: "Update the current organization from one JSON object on stdin; only the given keys change",
		Long:  orgUpdateHelp,
		Args:  cobra.NoArgs,
		RunE:  runOrgUpdate,
	}
	updateCmd.SetHelpFunc(orgFieldHelp(orgUpdateHelp, org.UpdateFields, false))

	cmd.AddCommand(createCmd, listCmd, updateCmd)
	return cmd
}

func runOrgCreate(cmd *cobra.Command, _ []string) error {
	in, err := orgStdin(cmd)
	if err != nil {
		return err
	}
	data, err := terminal.ReadStdin(in)
	if err != nil {
		return err
	}
	obj, err := terminal.ParseObject(data)
	if err != nil {
		return err
	}
	c, err := session.UserClient()
	if err != nil {
		return err
	}
	fields, err := org.LoadFields(c.BaseURL, org.CreateFields)
	if err != nil {
		return err
	}
	created, err := org.Create(c, fields, data, obj)
	if err != nil {
		return err
	}
	id := guardrail.OneLine(created.ID)
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created organization %s (%s). You are its owner; run 'oba org use %s' to work in it.\n",
		guardrail.OneLine(created.Name), id, id)
	return nil
}

// runOrgUpdate changes the given keys of the current organization. The names it prints come
// from the API, so they pass through guardrail.OneLine.
func runOrgUpdate(cmd *cobra.Command, _ []string) error {
	in, err := orgStdin(cmd)
	if err != nil {
		return err
	}
	given, err := terminal.ReadStdinObject(in)
	if err != nil {
		return err
	}
	if len(given) == 0 {
		return terminal.ErrNothingToUpdate
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	result, sent, err := org.Update(c, given)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	name := guardrail.OneLine(result.Name)
	if !sent {
		_, _ = fmt.Fprintf(out, "Nothing to update: %s already has these values.\n", name)
		return nil
	}
	_, _ = fmt.Fprintf(out, "Updated organization %s (%s)\n", name, guardrail.OneLine(result.ID))
	return nil
}

// orgStdin returns the input of create and update. A terminal on stdin means nothing was piped in, so the
// command fails at once instead of waiting for typed input.
func orgStdin(cmd *cobra.Command) (io.Reader, error) {
	if stdinIsTerminal() {
		return nil, fmt.Errorf("no JSON on stdin; pass one JSON object, as 'oba admin org %s --help' shows", cmd.Name())
	}
	return cmd.InOrStdin(), nil
}

// orgFieldHelp returns a help function that appends the live fields of source to the command's
// help, the required ones marked when markRequired is set. Without the API it prints the usage
// and the reason, and still succeeds.
func orgFieldHelp(intro string, source org.FieldSource, markRequired bool) func(*cobra.Command, []string) {
	return func(cmd *cobra.Command, args []string) {
		section := "Fields unavailable: "
		if apiURL, err := session.APIURL(); err != nil {
			section += err.Error() + "."
		} else {
			section = org.FieldSection(apiURL, source, markRequired)
		}
		cmd.Long = intro + "\n\n" + section
		cmd.Root().HelpFunc()(cmd, args)
	}
}
