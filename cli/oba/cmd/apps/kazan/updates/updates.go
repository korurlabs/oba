// Package updates builds the oba kazan updates commands: write, preview, list, read, edit and
// delete daily updates, and list the members who have not posted one.
package updates

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan updates command with its subcommands. Its alias "update" reads as
// daily updates, never as an upgrade of the CLI; the change subcommand is "edit", so that no
// command reads "updates update".
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:     "updates",
		Aliases: []string{"update"},
		Short:   "Write and read daily updates",
		Long: `Write your daily update from your tickets and read your colleagues' updates.

An update lists tickets with a percentage and an optional note, then what you
will focus on and your questions. Kazan renders it as paste-ready text in the
team's four-part format. A percentage you set that differs from the ticket's
progress also becomes the ticket's progress when you are OWNER, ADMIN or MEMBER
on its board.

Every member of an organization you share with the author sees the whole
update. With --json, each line carries canOpen: open a ticket from an update
only when it is true.

An update is expected from every member on every weekday by 10:30
Europe/Istanbul time. An organization's OWNERs and ADMINs see who has not
posted with 'oba kazan updates missing'.`,
	}
	cmd.AddCommand(newCreateCmd(), newPreviewCmd(), newListCmd(), newGetCmd(), newEditCmd(), newDeleteCmd(), newMissingCmd())
	return cmd
}

// interactive reports whether the command may ask a question: stdin is a terminal and the output
// is not --json.
func interactive() bool {
	return stdinIsTerminal() && !jsonout.Enabled()
}

// prompter returns the questions of the command on a terminal, nil without one.
func prompter(cmd *cobra.Command) *updates.Prompter {
	if !interactive() {
		return nil
	}
	p := updates.NewPrompter(cmd.InOrStdin(), cmd.OutOrStdout())
	return &p
}

// start returns a client in the current organization, which records the command's result for
// --json, and a resolver for the ticket and board lookups of the run.
func start(cmd *cobra.Command) (*client.Client, *ticket.Resolver, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, nil, err
	}
	return c, ticket.NewResolver(c, boardflag.Value(cmd), cmd.ErrOrStderr()), nil
}

// cancelled ends a run the user left without an answer: the result is null.
func cancelled(cmd *cobra.Command) error {
	jsonresult.Nothing()
	_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Cancelled.")
	return nil
}

// textFlag reads a --focus or --questions value and whether it was given.
func textFlag(cmd *cobra.Command, name, value string) updates.TextFlag {
	return updates.TextFlag{Value: value, Changed: cmd.Flags().Changed(name)}
}

// lineFlagsHelp explains --percent and --note, which create, preview and edit share.
const lineFlagsHelp = "--percent and --note name a ticket by key and can be repeated."

// addLineFlags adds the per-line flags that create, preview and edit share.
func addLineFlags(cmd *cobra.Command, percents, notes *[]string) {
	cmd.Flags().StringArrayVar(percents, "percent", nil, "KEY=N: the line's percentage, 0-100 (repeatable)")
	cmd.Flags().StringArrayVar(notes, "note", nil, "KEY=TEXT: the note under the line; KEY= clears it (repeatable)")
}
