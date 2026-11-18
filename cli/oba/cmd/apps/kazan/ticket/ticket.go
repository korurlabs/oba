// Package ticket builds the oba kazan ticket commands: list the tickets of a board, of every
// board or of the signed-in user, and read, create, change, delete, restore, move and order
// tickets.
package ticket

import (
	"bufio"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const ticketRefHelp = "A ticket is named by its key, such as OPS-42, whose prefix names its board, or by its id, " +
	"whose board is --board or the default board."

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan ticket command with its subcommands. It reads --board from the
// oba kazan group it is added to.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "ticket",
		Short: "List, read, create, change, delete, restore, move and order tickets",
		Long:  "Create, list, update, delete, move, and restore tickets on a Kazan board.\n\n" + ticketRefHelp,
	}
	cmd.AddCommand(
		newListCmd(), newAllCmd(), newMineCmd(), newGetCmd(), newCreateCmd(), newUpdateCmd(),
		newDeleteCmd(), newRestoreCmd(), newMoveCmd(), newBulkMoveCmd(), newBulkDeleteCmd(), newOrderCmd(),
	)
	return cmd
}

// start returns a client in the current organization, which records the command's result for
// --json, and a resolver for the lookups of the run.
func start(cmd *cobra.Command) (*client.Client, *ticket.Resolver, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, nil, err
	}
	return c, ticket.NewResolver(c, boardflag.Value(cmd), cmd.ErrOrStderr()), nil
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

// ticketArg returns the ticket the arguments name. The ticket is required: the picker of an
// interactive terminal is not offered.
func ticketArg(args []string) (string, error) {
	if len(args) == 0 || strings.TrimSpace(args[0]) == "" {
		return "", fmt.Errorf("name the ticket by its key, such as OPS-42, or its id")
	}
	return strings.TrimSpace(args[0]), nil
}

// changed returns the flag's value when the flag was given, else nil, so that an empty value
// given on purpose is still sent.
func changed(cmd *cobra.Command, name, value string) *string {
	if !cmd.Flags().Changed(name) {
		return nil
	}
	return &value
}

// changedInt is changed for a number flag.
func changedInt(cmd *cobra.Command, name string, value int) *int {
	if !cmd.Flags().Changed(name) {
		return nil
	}
	return &value
}

// warnRetiredType tells the user that --type TASK was sent as FEATURE.
func warnRetiredType(cmd *cobra.Command, retired bool) {
	if retired {
		_, _ = fmt.Fprintln(cmd.ErrOrStderr(), "warning: type TASK is now FEATURE")
	}
}
