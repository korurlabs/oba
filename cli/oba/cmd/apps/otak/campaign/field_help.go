package campaign

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// fieldHelp returns a help function that appends the live request fields to the command's help.
// Without the API it prints the usage and the reason, and still succeeds.
func fieldHelp(intro string, source campaign.FieldSource, markRequired bool) func(*cobra.Command, []string) {
	return func(cmd *cobra.Command, args []string) {
		section := "Fields unavailable: "
		if apiURL, err := session.APIURL(); err != nil {
			section += err.Error() + "."
		} else {
			section = campaign.FieldSection(apiURL, source, markRequired)
		}
		cmd.Long = intro + "\n\n" + section
		cmd.Root().HelpFunc()(cmd, args)
	}
}
