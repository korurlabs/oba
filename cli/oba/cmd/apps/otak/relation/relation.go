package relation

import (
	"bufio"
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	relationListHelp = `Lists the leads, customers and suppliers of the current organization with their ids, in one
table: NAME, TYPE, STAGE, WEBSITE, OWNER, CONTACT, CONTACT MAIL, COMPANY MAIL, ID. CONTACT and
CONTACT MAIL are the name and email of the relation's main person contact, the one to talk to
first; COMPANY MAIL is the email of its main business contact, such as info@. Every page is
read.

Without --type every type is listed, one series of calls per type, grouped in the API's type
order and, within a type, last change first. --type narrows to one type.

Without --stage every relation of the listed types is shown, at every stage: leads that are
LOST or PARKED and customers at CLOSED or DIRECT included. --stage only narrows, to leads and
customers at one of the given stages; repeat it or give a comma list. Without --type it reads
leads and customers and skips suppliers; with --type SUPPLIER it is refused, since suppliers
have no stage. CLOSED and DIRECT are customer stages; the other stages are lead stages, and a
stage of the other type matches nothing.

--search keeps the relations whose name contains the text, case ignored.

--event keeps the relations linked to one event, by its id ('oba otak event list' prints it).
An unknown event id lists nothing. --campaign does the same for one campaign ('oba otak
campaign list' prints its id).

Type and stage values are matched without regard to case against the live API, and an unknown
value is refused before any call.

  oba otak relation list --search acme
  oba otak relation list --type lead --stage new,warm
  oba otak relation list --stage closed
  oba otak relation list --event <eventId>`
	relationCreateHelp = `Creates one lead, customer or supplier in the current organization from one JSON object
on stdin. The keys are the API's field names and the values go to the API unchanged; the CLI
translates nothing. A key the API does not know is refused before anything is sent.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba otak relation create <<'EOF'
  {"name": "Acme B.V.", "type": "LEAD", "stage": "WARM", "country": "NL"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak relation create < relation.json`
	relationUpdateHelp = `Changes only the fields given in one JSON object on stdin; every other field keeps its value.
null clears a field. The keys are the API's field names, plus "type" and "stage"; the values
go to the API unchanged, and a key the API does not know is refused before anything is sent.

The update runs as up to three calls: the fields (PUT), then the type, then the stage, each
only when needed. The calls are not atomic: when one fails, the error names the steps already
applied and the later steps are not run.

A type change and the stage CLOSED convert the relation at once, without a prompt: CLOSED turns
a lead into a customer, and a type change sets the stage (NEW for a lead, CLOSED for a lead
that becomes a customer, DIRECT for a supplier that becomes one, none for a supplier). After a
conversion the command prints the new type and stage as the API set them.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba otak relation update <id> <<'EOF'
  {"stage": "PROPOSAL", "expectedValue": 25000.10}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak relation update <id> < relation.json`
)

// The relation types the conversion line names, as the API spells them.
const (
	relationTypeLead     = "LEAD"
	relationTypeCustomer = "CUSTOMER"
	relationTypeSupplier = "SUPPLIER"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "relation",
		Short: "List, create, update and delete leads, customers and suppliers",
	}

	var listQuery relation.ListQuery
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List relations with their ids, filtered by type, stage and name",
		Long:  relationListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runRelationList(cmd, listQuery)
		},
	}
	listCmd.Flags().StringVar(&listQuery.Type, "type", "", "list only this type: LEAD, CUSTOMER or SUPPLIER (default every type)")
	listCmd.Flags().StringSliceVar(&listQuery.Stages, "stage", nil, "list only leads and customers at these stages; repeat or give a comma list (default every stage)")
	listCmd.Flags().StringVar(&listQuery.Search, "search", "", "list only relations whose name contains this text, case ignored")
	listCmd.Flags().StringVar(&listQuery.EventID, "event", "", "list only relations linked to the event with this id")
	listCmd.Flags().StringVar(&listQuery.CampaignID, "campaign", "", "list only relations linked to the campaign with this id")

	createCmd := &cobra.Command{
		Use:   "create",
		Short: "Create a relation from one JSON object on stdin",
		Long:  relationCreateHelp,
		Args:  cobra.NoArgs,
		RunE:  runRelationCreate,
	}
	createCmd.SetHelpFunc(fieldHelp(relationCreateHelp, relation.CreateFields, true))

	updateCmd := &cobra.Command{
		Use:   "update <id>",
		Short: "Change the given fields of a relation from one JSON object on stdin",
		Long:  relationUpdateHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runRelationUpdate,
	}
	updateCmd.SetHelpFunc(fieldHelp(relationUpdateHelp, relation.UpdateFields, false))

	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete a relation; needs the ADMIN role",
		Long: "Deletes a relation after asking for confirmation. Without a terminal to ask on, pass --yes. " +
			"Only an admin or owner of the organization may delete.",
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runRelationDelete(cmd, args, deleteYes)
		},
	}
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, createCmd, updateCmd, deleteCmd)
	return cmd
}

func runRelationList(cmd *cobra.Command, query relation.ListQuery) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	items, err := relation.List(c, query)
	if err != nil {
		return err
	}
	return relation.PrintList(cmd.OutOrStdout(), items)
}

func runRelationCreate(cmd *cobra.Command, _ []string) error {
	in, err := commandStdin(cmd)
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
	created, err := relation.Create(c, data, obj)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created relation %s\n", created.ID)
	return nil
}

func runRelationUpdate(cmd *cobra.Command, args []string) error {
	id := strings.TrimSpace(args[0])
	in, err := commandStdin(cmd)
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
	result, err := relation.Update(c, id, given)
	if err != nil {
		return err
	}

	out := cmd.OutOrStdout()
	if len(result.Applied) == 0 {
		_, _ = fmt.Fprintf(out, "Nothing to update: relation %s already has these values.\n", id)
		return nil
	}
	_, _ = fmt.Fprintf(out, "Updated relation %s\n", id)
	if line := conversionLine(result.Before, result.After); line != "" {
		_, _ = fmt.Fprintln(out, line)
	}
	return nil
}

// conversionLine tells the user what a type change did to a relation, since the API sets the
// stage itself when the type changes and a stage of CLOSED turns a lead into a customer. Type
// and stage come from after, the relation as the API returned it, never from the input. It is
// empty when the type did not change.
func conversionLine(before, after client.Relation) string {
	if strings.EqualFold(before.Type, after.Type) {
		return ""
	}
	afterType := strings.ToUpper(after.Type)
	switch afterType {
	case relationTypeCustomer:
		if strings.EqualFold(before.Type, relationTypeLead) {
			return fmt.Sprintf("Relation %s is now a %s%s; it is no longer a lead.", after.ID, afterType, atStage(after.Stage))
		}
		return fmt.Sprintf("Relation %s is now a %s%s.", after.ID, afterType, atStage(after.Stage))
	case relationTypeLead:
		if after.Stage == nil {
			return fmt.Sprintf("Relation %s is a %s again.", after.ID, afterType)
		}
		return fmt.Sprintf("Relation %s is a %s again and restarted at stage %s.", after.ID, afterType, *after.Stage)
	case relationTypeSupplier:
		return fmt.Sprintf("Relation %s is now a %s; its stage is cleared.", after.ID, afterType)
	default:
		return fmt.Sprintf("Relation %s is now a %s%s.", after.ID, afterType, atStage(after.Stage))
	}
}

func atStage(stage *string) string {
	if stage == nil {
		return ""
	}
	return " at stage " + *stage
}

func runRelationDelete(cmd *cobra.Command, args []string, yes bool) error {
	id := strings.TrimSpace(args[0])
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
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete relation %s?", id))
		if err != nil {
			return err
		}
		if !confirmed {
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := relation.Delete(c, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted relation %s\n", id)
	return nil
}

// commandStdin returns the input of create and update. A terminal on stdin means nothing was
// piped in, so the command fails at once instead of waiting for typed input.
func commandStdin(cmd *cobra.Command) (io.Reader, error) {
	if stdinIsTerminal() {
		return nil, fmt.Errorf("no JSON on stdin; pass one JSON object, as 'oba otak relation %s --help' shows", cmd.Name())
	}
	return cmd.InOrStdin(), nil
}

// fieldHelp returns a help function that appends the live field template to the command's
// help. Help works without a session: the API URL then comes from the environment or the
// default. Without the API it prints the usage and the reason, and still succeeds.
func fieldHelp(intro string, source relation.FieldSource, markRequired bool) func(*cobra.Command, []string) {
	return func(cmd *cobra.Command, args []string) {
		cmd.Long = intro + "\n\n" + fieldSection(source, markRequired)
		cmd.Root().HelpFunc()(cmd, args)
	}
}

func fieldSection(source relation.FieldSource, markRequired bool) string {
	apiURL, err := session.APIURL()
	if err != nil {
		return fmt.Sprintf("Fields unavailable: %v.", err)
	}
	return relation.FieldSection(apiURL, source, markRequired)
}
