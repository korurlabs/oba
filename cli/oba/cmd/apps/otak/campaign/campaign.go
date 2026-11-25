// Package campaign builds the oba otak campaign commands: list, create, update and delete the
// posts and mailings of the organization, one per channel, with a text per language.
package campaign

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds oba otak campaign with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "campaign",
		Short: "List, create, update and delete campaigns: posts and mailings with a text per language",
		Long: "A campaign is one post or mailing of the organization on one channel (LINKEDIN, X or NEWSLETTER), " +
			"with its text in NL, TR and EN and its images as attachments ('oba otak attachment add --campaign <id>'). " +
			"Oba does not post it: plan it as SCHEDULED with plannedAt, post it by hand, then mark it PUBLISHED.",
	}
	cmd.AddCommand(newListCmd(), newCreateCmd(), newUpdateCmd(), newDeleteCmd())
	return cmd
}
