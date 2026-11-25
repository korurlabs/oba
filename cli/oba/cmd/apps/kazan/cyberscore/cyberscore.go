// Package cyberscore builds the oba kazan cyberscore commands: read the cyberscore of the
// organization and its trend, and list, read, create, change and delete the checks behind it.
package cyberscore

import (
	"bufio"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const checkRefHelp = "A check is named by its id or its name among the checks of the organization; a name " +
	"matches exactly, else with case ignored. A name of several words needs no quotes."

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan cyberscore command with its subcommands. The score belongs to the
// organization, so no subcommand reads --board.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "cyberscore",
		Short: "Read the cyberscore of the organization and manage its checks",
		Long: "The cyberscore is the security score of the current organization: the weighted result of its " +
			"checks, with a level, one percentage per category and a daily trend. It belongs to the " +
			"organization, not to a board, so --board does not apply; --org selects another organization.\n\n" +
			"Every member may read the score and the checks; only an owner or admin may change the checks.",
	}
	cmd.AddCommand(newShowCmd(), newTrendCmd(), newCheckCmd())
	return cmd
}

// checkArg joins the arguments, so that a name of several words needs no quotes. An empty
// result is refused by the cyberscore package before any call.
func checkArg(args []string) string {
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

// changedInt returns the number flag's value when the flag was given, else nil.
func changedInt(cmd *cobra.Command, name string, value int) *int {
	if !cmd.Flags().Changed(name) {
		return nil
	}
	return &value
}
