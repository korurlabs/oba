// Package event builds the oba otak event commands: list, create, update and delete the events
// where the organization meets companies.
package event

import (
	"bufio"
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/event"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	eventListHelp = `Lists the events of the current organization (expos, conferences, meetups, webinars) in one
table: NAME, TYPE, START, END, PLACE, ID. Every page is read.

--period upcoming lists the events whose last day (the end date, else the start date) is today or
later, soonest first; --period past lists the others, most recent first. Today is this
machine's calendar day. Without --period every event is listed, most recent start first.

--search keeps the events whose name contains the text, case ignored.

  oba otak event list --period upcoming
  oba otak event list --search igef`
	eventCreateHelp = `Creates one event in the current organization from one JSON object on stdin. The keys are the
API's field names and the values go to the API unchanged; a key the API does not know is refused
before anything is sent. Dates are calendar days, YYYY-MM-DD; the end date is optional and not
before the start.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba otak event create <<'EOF'
  {"name": "Textile Expo 2027", "type": "EXPO", "startDate": "2027-03-09", "endDate": "2027-03-11",
   "city": "Amsterdam", "country": "NL", "website": "https://expo.example.com/"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak event create < event.json

Link a relation to the event with 'oba otak relation update <id>' and {"eventId": "<eventId>"}.`
	eventUpdateHelp = `Changes only the fields given in one JSON object on stdin; every other field keeps its value.
null clears a field. The keys are the API's field names; the values go to the API unchanged, and
a key the API does not know is refused before anything is sent.

  oba otak event update <id> <<'EOF'
  {"endDate": "2026-11-07", "notes": null}
  EOF`
)

// stdinIsTerminal and today are variables so that tests can stand in for a terminal and a day.
var (
	stdinIsTerminal = terminal.StdinIsTerminal
	today           = func() string { return time.Now().Format(time.DateOnly) }
)

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "event",
		Short: "List, create, update and delete events: expos, conferences, meetups and webinars",
	}

	var listQuery event.ListQuery
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the events of the organization, upcoming or past",
		Long:  eventListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runEventList(cmd, listQuery)
		},
	}
	listCmd.Flags().StringVar(&listQuery.Period, "period", "", "list only upcoming or past events (default every event)")
	listCmd.Flags().StringVar(&listQuery.Search, "search", "", "list only events whose name contains this text, case ignored")

	createCmd := &cobra.Command{
		Use:   "create",
		Short: "Create an event from one JSON object on stdin",
		Long:  eventCreateHelp,
		Args:  cobra.NoArgs,
		RunE:  runEventCreate,
	}
	createCmd.SetHelpFunc(eventFieldHelp(eventCreateHelp, event.CreateFields, true))

	updateCmd := &cobra.Command{
		Use:   "update <id>",
		Short: "Change the given fields of an event from one JSON object on stdin",
		Long:  eventUpdateHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runEventUpdate,
	}
	updateCmd.SetHelpFunc(eventFieldHelp(eventUpdateHelp, event.UpdateFields, false))

	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete an event; needs the ADMIN role",
		Long: "Deletes an event after asking for confirmation; the relations linked to it stay and lose the link. " +
			"Without a terminal to ask on, pass --yes. Only an admin or owner of the organization may delete.",
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runEventDelete(cmd, args, deleteYes)
		},
	}
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, createCmd, updateCmd, deleteCmd)
	return cmd
}

func runEventList(cmd *cobra.Command, query event.ListQuery) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	query.Today = today()
	events, err := event.List(c, query)
	if err != nil {
		return err
	}
	return event.PrintList(cmd.OutOrStdout(), events)
}

func runEventCreate(cmd *cobra.Command, _ []string) error {
	in, err := eventStdin(cmd)
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
	fields, err := event.LoadFields(c.BaseURL, event.CreateFields)
	if err != nil {
		return err
	}
	created, err := event.Create(c, fields, data, obj)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created event %s (%s)\n", created.Name, created.ID)
	return nil
}

func runEventUpdate(cmd *cobra.Command, args []string) error {
	id := strings.TrimSpace(args[0])
	in, err := eventStdin(cmd)
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
	if _, err := event.Update(c, id, given); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated event %s\n", id)
	return nil
}

func runEventDelete(cmd *cobra.Command, args []string, yes bool) error {
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
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete event %s?", id))
		if err != nil {
			return err
		}
		if !confirmed {
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := event.Delete(c, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted event %s\n", id)
	return nil
}

// eventStdin returns the input of create and update. A terminal on stdin means nothing was
// piped in, so the command fails at once instead of waiting for typed input.
func eventStdin(cmd *cobra.Command) (io.Reader, error) {
	if stdinIsTerminal() {
		return nil, fmt.Errorf("no JSON on stdin; pass one JSON object, as 'oba otak event %s --help' shows", cmd.Name())
	}
	return cmd.InOrStdin(), nil
}

// eventFieldHelp returns a help function that appends the live request fields to the command's
// help. Without the API it prints the usage and the reason, and still succeeds.
func eventFieldHelp(intro string, source event.FieldSource, markRequired bool) func(*cobra.Command, []string) {
	return func(cmd *cobra.Command, args []string) {
		section := "Fields unavailable: "
		if apiURL, err := session.APIURL(); err != nil {
			section += err.Error() + "."
		} else {
			section = event.FieldSection(apiURL, source, markRequired)
		}
		cmd.Long = intro + "\n\n" + section
		cmd.Root().HelpFunc()(cmd, args)
	}
}
