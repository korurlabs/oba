package shrine

import (
	"bufio"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// newLegalEntityCmd builds oba legal-entity with its list, get, create, update and delete
// subcommands. Every subcommand acts in the current organization, or the one --org names.
func newLegalEntityCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "legal-entity",
		Short: "List, read, create, change and delete the legal entities of the organization",
		Long: "A legal entity is a company in whose name the organization issues documents: its short name, " +
			"legal name, country, registration details, and the governing law and court its contracts name. " +
			"The commands act in the current organization ('oba org use'), or the one --org names. Reading " +
			"needs the MEMBER role; create, update and delete need ADMIN.",
	}
	cmd.AddCommand(newLegalEntityListCmd(), newLegalEntityGetCmd(), newLegalEntityCreateCmd(),
		newLegalEntityUpdateCmd(), newLegalEntityDeleteCmd())
	return cmd
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
