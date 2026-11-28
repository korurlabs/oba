// Package campaign builds the oba otak campaign commands: list, create, update and delete the
// campaigns of the organization, the umbrellas of related posts and mailings, and through
// 'campaign content' their contents.
package campaign

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/otak/campaign/content"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds oba otak campaign with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "campaign",
		Short: "List, create, update and delete campaigns and their contents",
		Long: "A campaign is an umbrella of the organization: a name, an optional description and date range, " +
			"owner, event and ICP. It holds any number of contents ('oba otak campaign content'); a content is one " +
			"thing actually shared, such as one LinkedIn post, in one language, published by one legal entity. " +
			"Translations of one post are contents of the same campaign.",
	}
	cmd.AddCommand(newListCmd(), newCreateCmd(), newUpdateCmd(), newDeleteCmd(), content.NewCmd())
	return cmd
}
