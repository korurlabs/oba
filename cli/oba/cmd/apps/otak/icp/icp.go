package icp

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/signal"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/icp"
	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	icpCreateHelp = `Creates one ICP (Ideal Customer Profile) in the current organization.

In a terminal, the command asks you to describe your ideal customer and has Claude Code, on
this machine and under your own Claude account, draft the ICP from it with the fields and
allowed values of the live API. It prints the draft and creates it only when you answer y or
yes; n or no cancels. This needs Claude Code (https://claude.com/claude-code), signed in once
by running 'claude'.

Type or paste the description over as many lines as you like, blank lines included, and finish
with Ctrl-D on a line of its own. Ctrl-D with nothing typed cancels; Ctrl-C stops the command at
any point, and nothing is created.

With one JSON object on stdin, the command creates the ICP from that object without Claude
Code. The keys are the API's field names and the values go to the API unchanged; a key the API
does not know is refused before anything is sent:

  oba otak icp create <<'EOF'
  {"name": "Dutch textile SMEs", "industry": "MANUFACTURING", "country": "NL"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak icp create < icp.json`
	icpUpdateHelp = `Changes only the fields given in one JSON object on stdin; every other field keeps its value.
null clears a field. The keys are the API's field names; the values go to the API unchanged, and
a key the API does not know is refused before anything is sent.

  oba otak icp update <id> <<'EOF'
  {"country": null, "targetPersonRole": "CISO"}
  EOF`
)

// errIcpCreateNotInteractive is printed as it is, so it keeps the full stop.
var errIcpCreateNotInteractive = errors.New("oba otak icp create drafts with Claude Code only in a terminal; pipe one JSON object to create without it.") //nolint:staticcheck // ST1005: shown to the user verbatim

// claudeInstalled, runClaude, stdinIsTerminal and stdoutIsTerminal are variables so that tests
// can stand in for Claude Code and a terminal.
var (
	claudeInstalled  = claude.Installed
	runClaude        = claude.Run
	stdinIsTerminal  = terminal.StdinIsTerminal
	stdoutIsTerminal = terminal.StdoutIsTerminal
)

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "icp",
		Short: "List, create, brainstorm, update and delete ICPs (Ideal Customer Profiles)",
	}

	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the ICPs of the organization",
		Args:  cobra.NoArgs,
		RunE:  runIcpList,
	}

	createCmd := &cobra.Command{
		Use:   "create",
		Short: "Create an ICP: drafted with Claude Code in a terminal, or from one JSON object on stdin",
		Long:  icpCreateHelp,
		Args:  cobra.NoArgs,
		RunE:  runIcpCreate,
	}
	createCmd.SetHelpFunc(icpFieldHelp(icpCreateHelp, icp.CreateFields, true))

	updateCmd := &cobra.Command{
		Use:   "update <id>",
		Short: "Change the given fields of an ICP from one JSON object on stdin",
		Long:  icpUpdateHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runIcpUpdate,
	}
	updateCmd.SetHelpFunc(icpFieldHelp(icpUpdateHelp, icp.UpdateFields, false))

	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete an ICP; needs the ADMIN role",
		Long: "Deletes an ICP after asking for confirmation; the relations linked to it stay and lose the link. " +
			"Without a terminal to ask on, pass --yes. Only an admin or owner of the organization may delete.",
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runIcpDelete(cmd, args, deleteYes)
		},
	}
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, createCmd, newIcpBrainstormCmd(), updateCmd, deleteCmd)
	return cmd
}

func runIcpList(cmd *cobra.Command, _ []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	icps, err := icp.List(c)
	if err != nil {
		return err
	}
	return icp.PrintList(cmd.OutOrStdout(), icps)
}

func runIcpCreate(cmd *cobra.Command, _ []string) error {
	if stdinIsTerminal() {
		return runIcpDraft(cmd)
	}
	data, err := terminal.ReadStdin(cmd.InOrStdin())
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
	fields, err := icp.LoadFields(c.BaseURL, icp.CreateFields)
	if err != nil {
		return err
	}
	created, err := icp.Create(c, fields, data, obj)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created ICP %s (%s)\n", created.Name, created.ID)
	return nil
}

// runIcpDraft has Claude Code draft the ICP from the description the user types, shows the
// draft and creates it once the user confirms.
func runIcpDraft(cmd *cobra.Command) error {
	if !stdoutIsTerminal() {
		return errIcpCreateNotInteractive
	}
	if err := claudeInstalled(); err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	fields, err := icp.LoadFields(c.BaseURL, icp.CreateFields)
	if err != nil {
		return err
	}
	schema, err := icp.DraftSchema(fields)
	if err != nil {
		return err
	}
	src := cmd.InOrStdin()
	in := bufio.NewReader(src)
	out := cmd.OutOrStdout()

	description, err := icpDescription(in, out)
	if err != nil {
		return err
	}
	if description == "" {
		_, _ = fmt.Fprintln(out, "Cancelled.")
		return nil
	}

	_, _ = fmt.Fprintln(out, "Drafting the ICP with Claude Code. Ctrl-C stops it.")
	ctx, stop := signal.NotifyContext(cmd.Context(), os.Interrupt)
	result, err := runClaude(ctx, icp.Brief(description, fields), schema, func(line string) {
		_, _ = fmt.Fprintln(out, "  "+line)
	})
	stop()
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return errors.New("stopped; nothing was created")
		}
		return err
	}
	draft, err := icp.ParseDraft(result, fields)
	if err != nil {
		return err
	}
	icp.PrintDraft(out, draft, fields)

	confirmed, err := terminal.Confirm(in, src, out, "Create this ICP?")
	if err != nil {
		return err
	}
	if !confirmed {
		_, _ = fmt.Fprintln(out, "Cancelled.")
		return nil
	}
	body, err := json.Marshal(draft)
	if err != nil {
		return fmt.Errorf("failed to encode the draft: %w", err)
	}
	created, err := icp.Create(c, fields, body, draft)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Created ICP %s (%s)\n", created.Name, created.ID)
	return nil
}

// icpDescription reads the description from the terminal: every line up to Ctrl-D, so that a
// pasted text of several paragraphs is read whole and none of its lines is left over to answer
// a later question or reach the shell.
func icpDescription(in *bufio.Reader, out io.Writer) (string, error) {
	_, _ = fmt.Fprintln(out, "Describe your ideal customer. Several lines are fine; finish with Ctrl-D on a line of its own,")
	_, _ = fmt.Fprintln(out, "or press Ctrl-D at once to cancel.")
	return terminal.ReadText(in)
}

func runIcpUpdate(cmd *cobra.Command, args []string) error {
	id := strings.TrimSpace(args[0])
	in, err := icpStdin(cmd)
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
	if _, err := icp.Update(c, id, given); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated ICP %s\n", id)
	return nil
}

func runIcpDelete(cmd *cobra.Command, args []string, yes bool) error {
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
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete ICP %s?", id))
		if err != nil {
			return err
		}
		if !confirmed {
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := icp.Delete(c, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted ICP %s\n", id)
	return nil
}

// icpStdin returns the input of update. A terminal on stdin means nothing was piped in, so the
// command fails at once instead of waiting for typed input.
func icpStdin(cmd *cobra.Command) (io.Reader, error) {
	if stdinIsTerminal() {
		return nil, fmt.Errorf("no JSON on stdin; pass one JSON object, as 'oba otak icp %s --help' shows", cmd.Name())
	}
	return cmd.InOrStdin(), nil
}

// icpFieldHelp returns a help function that appends the live request fields to the command's
// help. Without the API it prints the usage and the reason, and still succeeds.
func icpFieldHelp(intro string, source icp.FieldSource, markRequired bool) func(*cobra.Command, []string) {
	return func(cmd *cobra.Command, args []string) {
		section := "Fields unavailable: "
		if apiURL, err := session.APIURL(); err != nil {
			section += err.Error() + "."
		} else {
			section = icp.FieldSection(apiURL, source, markRequired)
		}
		cmd.Long = intro + "\n\n" + section
		cmd.Root().HelpFunc()(cmd, args)
	}
}
