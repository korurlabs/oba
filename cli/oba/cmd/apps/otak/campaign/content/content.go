// Package content builds the oba otak campaign content commands: list, create, update and delete
// the contents of one campaign, each one post or mailing in one language from one legal entity.
package content

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds oba otak campaign content with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "content",
		Short: "List, create, update and delete the contents of a campaign",
		Long: "A content is one thing actually shared, such as one LinkedIn post: one channel (LINKEDIN, X or " +
			"NEWSLETTER), one language (NL, TR or EN), one text, published by one legal entity of the organization. " +
			"Its images are attachments ('oba otak attachment add --content <contentId>'). Oba does not post it: " +
			"plan it as SCHEDULED with plannedAt, post it by hand, then mark it PUBLISHED.",
	}
	cmd.AddCommand(newListCmd(), newCreateCmd(), newUpdateCmd(), newDeleteCmd())
	return cmd
}
