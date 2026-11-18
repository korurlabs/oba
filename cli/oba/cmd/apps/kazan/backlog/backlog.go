// Package backlog builds the oba kazan backlog commands: show the backlog of a board and move a
// ticket into it.
package backlog

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/backlog"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// NewCmd builds the oba kazan backlog command with get and add-ticket. Both act on the board of
// --board, else the default board.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "backlog",
		Short: "Show a board's backlog and move tickets into it",
		Long: "The backlog of a board holds its tickets that are in no sprint. The board is --board, else the " +
			"default board chosen with 'oba kazan board use'.",
	}
	getCmd := &cobra.Command{
		Use:   "get",
		Short: "Show the backlog of the board",
		Args:  cobra.NoArgs,
		RunE:  runGet,
	}
	addTicketCmd := &cobra.Command{
		Use:   "add-ticket <ticketId>",
		Short: "Move a ticket of the board into its backlog",
		Args:  cobra.ExactArgs(1),
		RunE:  runAddTicket,
	}
	cmd.AddCommand(getCmd, addTicketCmd)
	return cmd
}

func runGet(cmd *cobra.Command, _ []string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	b, err := backlog.Get(c, boardID)
	if err != nil {
		return err
	}
	return backlog.Print(cmd.OutOrStdout(), b)
}

func runAddTicket(cmd *cobra.Command, args []string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	if err := backlog.AddTicket(c, boardID, args[0]); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Ticket %s added to backlog.\n", args[0])
	return nil
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
