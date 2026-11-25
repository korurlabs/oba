package content

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign/content"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const updateHelp = `Changes only the fields given in one JSON object on stdin; every other field keeps its value.
null clears a field. The keys are the API's field names; the values go to the API unchanged, and
a key the API does not know is refused before anything is sent.

  oba otak campaign content update <campaignId> <contentId> <<'EOF'
  {"status": "PUBLISHED", "postUrl": "https://www.linkedin.com/feed/update/urn:li:activity:1/"}
  EOF`

func newUpdateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "update <campaignId> <contentId>",
		Short: "Change the given fields of a content from one JSON object on stdin",
		Long:  updateHelp,
		Args:  cobra.ExactArgs(2),
		RunE:  runUpdate,
	}
	cmd.SetHelpFunc(fieldHelp(updateHelp, false))
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string) error {
	campaignID := strings.TrimSpace(args[0])
	contentID := strings.TrimSpace(args[1])
	in, err := contentStdin(cmd)
	if err != nil {
		return err
	}
	given, err := terminal.ReadStdinObject(in)
	if err != nil {
		return err
	}
	if len(given) == 0 {
		return terminal.ErrNothingToUpdate
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	updated, err := content.Update(c, campaignID, contentID, given)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated content %s (%s)\n", contentID, updated.Status)
	return nil
}
