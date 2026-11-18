// Package sprint builds the oba kazan sprint commands: list, read, create, change, start,
// complete, link, unlink, delete and restore the sprints of a board.
package sprint

import (
	"bufio"
	"errors"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const sprintRefHelp = "A sprint is named by its name, case ignored; when several share it, the running one wins. " +
	"The word 'sprint' means the running sprint of the board, else its newest. A name of several words " +
	"needs no quotes."

// errNoSprint refuses a command that names no sprint. A picker is not offered: the command must
// run the same way in a script and on a terminal.
var errNoSprint = errors.New("name the sprint, such as 'Sprint 4', or 'sprint' for the running one")

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan sprint command with its subcommands. Every subcommand acts on the
// board of --board, else the default board.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "sprint",
		Short: "List, read, create, change, start, complete, link, unlink, delete and restore sprints",
		Long: "Create, list, start, complete, and manage the sprints of a Kazan board. The board is --board, else " +
			"the default board chosen with 'oba kazan board use'.\n\n" + sprintRefHelp,
	}
	cmd.AddCommand(
		newListCmd(), newGetCmd(), newCreateCmd(), newUpdateCmd(), newStartCmd(), newCompleteCmd(),
		newLinkCmd(), newUnlinkCmd(), newDeleteCmd(), newRestoreCmd(),
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

// boardResolver resolves board names, prefixes and ids in the organization of c, reading the
// board tree once per run.
func boardResolver(c *client.Client) sprint.BoardResolver {
	r := board.NewResolver(c)
	return func(ref string) (string, error) {
		named, err := r.Resolve(ref)
		if err != nil {
			return "", err
		}
		return named.ID, nil
	}
}

// sprintArg returns the sprint the arguments name; the words are joined, so that Sprint 4 needs
// no quotes.
func sprintArg(args []string) (string, error) {
	name := strings.TrimSpace(strings.Join(args, " "))
	if name == "" {
		return "", errNoSprint
	}
	return name, nil
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

// addGoalFlags adds --goal and its hidden alias --description, the name the sprint goal had in
// older scripts.
func addGoalFlags(cmd *cobra.Command, goal, description *string, usage string) {
	cmd.Flags().StringVar(goal, "goal", "", usage)
	cmd.Flags().StringVar(description, "description", "", "alias of --goal")
	_ = cmd.Flags().MarkHidden("description")
}

// goalFlag returns the goal from --goal, else from --description, or nil when neither was given.
func goalFlag(cmd *cobra.Command, goal, description string) *string {
	if v := changed(cmd, "goal", goal); v != nil {
		return v
	}
	return changed(cmd, "description", description)
}
