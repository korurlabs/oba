package contact

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	contactListHelp = `Lists the contacts of one relation: name, KIND (person, or business for the company's
general details such as info@), MAIN (yes for the relation's main contact of that kind), role,
email, email type, phone, the page the contact was found on (SOURCE) and the contact id. The
main person contact comes first, then the main business contact, then the others by name.

The relation id is printed by 'oba otak relation create' and 'oba otak find', and listed by
'oba otak relation list --search <name>'.`
	contactCreateHelp = `Creates one contact person of a relation from one JSON object on stdin. The keys are the API's
field names and the values go to the API unchanged; the CLI translates nothing. A key the API
does not know is refused before anything is sent.

sourceUrl is the page the person's details were found on, a full http or https address. It is
set on create only: the person must be told where their details came from, so an update never
changes it.

kind is PERSON (a person at the relation) or BUSINESS (the company's general details, such as
info@ or sales@). kind, decisionMaker, mainPersonContact and mainBusinessContact are required.
The first contact of each kind becomes the relation's main contact of that kind; true in the
flag of its own kind makes the new contact main, and a true flag of the other kind is refused.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba otak contact create <relationId> <<'EOF'
  {"name": "Jan de Vries", "kind": "PERSON", "role": "Managing director",
   "email": "jan@acme.example", "decisionMaker": true, "mainPersonContact": false,
   "mainBusinessContact": false, "sourceUrl": "https://www.acme.example/team"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak contact create <relationId> < contact.json`
	contactUpdateHelp = `Changes only the fields given in one JSON object on stdin; every other field keeps its value.
null clears a field. The keys are the API's field names; the values go to the API unchanged, and
a key the API does not know is refused before anything is sent. sourceUrl is refused: it is set
on create only.

  oba otak contact update <relationId> <contactId> <<'EOF'
  {"phone": null, "decisionMaker": true}
  EOF

kind is PERSON (a person at the relation) or BUSINESS (the company's general details, such as
info@ or sales@); an update without it keeps the kind. Each kind has one main contact:
{"mainPersonContact": true} makes a person the relation's main contact, the one to talk to
first, and {"mainBusinessContact": true} makes a business contact the main business contact,
whose address the relation list shows. The previous main contact of that kind is unmarked in
the same call. false on the main contact of its kind is refused: mark another contact of the
kind main instead. A true flag of the other kind is refused.

  oba otak contact update <relationId> <contactId> <<'EOF'
  {"mainPersonContact": true}
  EOF

A kind change sends the main flag of the old kind as false, so the API makes another contact of
that kind main:

  oba otak contact update <relationId> <contactId> <<'EOF'
  {"kind": "BUSINESS"}
  EOF`
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "contact",
		Short: "List, create, update, delete and fill the contact persons of a relation",
	}

	listCmd := &cobra.Command{
		Use:   "list <relationId>",
		Short: "List the contacts of a relation",
		Long:  contactListHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runContactList,
	}

	createCmd := &cobra.Command{
		Use:   "create <relationId>",
		Short: "Create a contact of a relation from one JSON object on stdin",
		Long:  contactCreateHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runContactCreate,
	}
	createCmd.SetHelpFunc(contactFieldHelp(contactCreateHelp, contact.CreateFields, true))

	updateCmd := &cobra.Command{
		Use:   "update <relationId> <contactId>",
		Short: "Change the given fields of a contact from one JSON object on stdin",
		Long:  contactUpdateHelp,
		Args:  cobra.ExactArgs(2),
		RunE:  runContactUpdate,
	}
	updateCmd.SetHelpFunc(contactFieldHelp(contactUpdateHelp, contact.UpdateFields, false))

	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <relationId> <contactId>",
		Short: "Delete a contact of a relation",
		Long: "Deletes a contact after asking for confirmation. Without a terminal to ask on, pass --yes. " +
			"Any member of the organization may delete a contact.",
		Args: cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runContactDelete(cmd, args, deleteYes)
		},
	}
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, createCmd, updateCmd, deleteCmd, newContactFillCmd())
	return cmd
}

func runContactList(cmd *cobra.Command, args []string) error {
	relationID := strings.TrimSpace(args[0])
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	contacts, err := contact.List(c, relationID)
	if err != nil {
		return err
	}
	return contact.PrintList(cmd.OutOrStdout(), contacts)
}

func runContactCreate(cmd *cobra.Command, args []string) error {
	relationID := strings.TrimSpace(args[0])
	in, err := contactStdin(cmd)
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
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	fields, err := contact.LoadFields(c.BaseURL, contact.CreateFields)
	if err != nil {
		return err
	}
	created, err := contact.Create(c, relationID, fields, data, obj)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created contact %s\n", created.ID)
	return nil
}

func runContactUpdate(cmd *cobra.Command, args []string) error {
	relationID := strings.TrimSpace(args[0])
	contactID := strings.TrimSpace(args[1])
	in, err := contactStdin(cmd)
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
	updated, sent, err := contact.Update(c, relationID, contactID, given)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !sent {
		// The last call read every contact of the relation; the result is the one contact.
		value, err := json.Marshal(updated.Values)
		if err != nil {
			return fmt.Errorf("failed to encode the contact: %w", err)
		}
		if err := jsonresult.Set(value); err != nil {
			return err
		}
		_, _ = fmt.Fprintf(out, "Nothing to update: contact %s already has these values.\n", contactID)
		return nil
	}
	_, _ = fmt.Fprintf(out, "Updated contact %s\n", contactID)
	return nil
}

func runContactDelete(cmd *cobra.Command, args []string, yes bool) error {
	relationID := strings.TrimSpace(args[0])
	contactID := strings.TrimSpace(args[1])
	if !yes && !stdinIsTerminal() {
		return terminal.ErrDeleteNeedsYes
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes {
		src := cmd.InOrStdin()
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete contact %s?", contactID))
		if err != nil {
			return err
		}
		if !confirmed {
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := contact.Delete(c, relationID, contactID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted contact %s\n", contactID)
	return nil
}

// contactStdin returns the input of create and update. A terminal on stdin means nothing was
// piped in, so the command fails at once instead of waiting for typed input.
func contactStdin(cmd *cobra.Command) (io.Reader, error) {
	if stdinIsTerminal() {
		return nil, fmt.Errorf("no JSON on stdin; pass one JSON object, as 'oba otak contact %s --help' shows", cmd.Name())
	}
	return cmd.InOrStdin(), nil
}

// contactFieldHelp returns a help function that appends the live request fields to the
// command's help. Help works without a session: the API URL then comes from the environment or
// the default. Without the API it prints the usage and the reason, and still succeeds.
func contactFieldHelp(intro string, source contact.FieldSource, markRequired bool) func(*cobra.Command, []string) {
	return func(cmd *cobra.Command, args []string) {
		section := "Fields unavailable: "
		if apiURL, err := session.APIURL(); err != nil {
			section += err.Error() + "."
		} else {
			section = contact.FieldSection(apiURL, source, markRequired)
		}
		cmd.Long = intro + "\n\n" + section
		cmd.Root().HelpFunc()(cmd, args)
	}
}
