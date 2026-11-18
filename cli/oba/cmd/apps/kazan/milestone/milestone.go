// Package milestone builds the oba kazan milestone commands: list, read, create, change, close,
// reopen, restore and delete the milestones of a board, attach tickets to them and record the
// dependencies between them.
package milestone

import (
	"bufio"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const milestoneRefHelp = "A milestone is named by its id or its name, case ignored, among the milestones of the board " +
	"and its related boards. Names are not unique: a name several milestones share is an error that " +
	"lists them, so pass the id instead. A name of several words needs no quotes."

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan milestone command with its subcommands. Every subcommand acts on
// the board of --board, else the default board.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "milestone",
		Short: "List, read, create, change, close, delete milestones and link tickets to them",
		Long: "Create, list, close, and manage the dated milestones of a Kazan board. The board is --board, else " +
			"the default board chosen with 'oba kazan board use'.\n\n" + milestoneRefHelp,
	}
	cmd.AddCommand(
		newListCmd(), newGetCmd(), newCreateCmd(), newUpdateCmd(),
		newCloseCmd(), newReopenCmd(), newRestoreCmd(), newDeleteCmd(),
		newLinkCmd(), newUnlinkCmd(), newDependCmd(), newUndependCmd(),
	)
	return cmd
}

// selectBoard returns a client in the current organization and the id of the board the command
// acts on.
func selectBoard(cmd *cobra.Command) (*client.Client, string, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, "", err
	}
	ref, err := boardflag.Selected(cmd, c)
	if err != nil {
		return nil, "", err
	}
	return c, ref.ID, nil
}

// milestoneArg joins the arguments, so that a name of several words needs no quotes. An empty
// result is refused by the milestone package before any call.
func milestoneArg(args []string) string {
	return strings.TrimSpace(strings.Join(args, " "))
}

// interactive reports whether the command may ask a question: stdin is a terminal and the output
// is not --json.
func interactive() bool {
	return stdinIsTerminal() && !jsonout.Enabled()
}

// confirm asks question on the terminal and reports whether the answer was yes.
func confirm(cmd *cobra.Command, question string) (bool, error) {
	src := cmd.InOrStdin()
	return terminal.Confirm(bufio.NewReader(src), src, cmd.OutOrStdout(), question)
}

// changed returns the flag's value when the flag was given, else nil, so that an empty value
// given on purpose is still sent.
func changed(cmd *cobra.Command, name, value string) *string {
	if !cmd.Flags().Changed(name) {
		return nil
	}
	return &value
}
