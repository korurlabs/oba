package content

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign/content"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const createHelp = `Creates one content of a campaign from one JSON object on stdin. The keys are the API's field
names and the values go to the API unchanged; a key the API does not know is refused before
anything is sent. legalEntityId, channel, language and text are required; --legal-entity <id>
sets legalEntityId and replaces one given in the object. The status is DRAFT unless given;
SCHEDULED needs plannedAt, and PUBLISHED without publishedAt stores now. Moments are RFC 3339
instants, such as 2027-03-09T09:00:00Z.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba otak campaign content create <campaignId> --legal-entity <legalEntityId> <<'EOF'
  {"channel": "LINKEDIN", "language": "NL", "text": "...",
   "status": "SCHEDULED", "plannedAt": "2027-03-09T09:00:00Z"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak campaign content create <campaignId> --legal-entity <legalEntityId> < post.json

Add images with 'oba otak attachment add --content <contentId> --file <path>'.`

func newCreateCmd() *cobra.Command {
	var legalEntityID string
	cmd := &cobra.Command{
		Use:   "create <campaignId>",
		Short: "Create a content of a campaign from one JSON object on stdin",
		Long:  createHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runCreate(cmd, strings.TrimSpace(args[0]), strings.TrimSpace(legalEntityID))
		},
	}
	cmd.Flags().StringVar(&legalEntityID, "legal-entity", "", "the id of the legal entity that publishes the content; replaces legalEntityId")
	cmd.SetHelpFunc(fieldHelp(createHelp, true))
	return cmd
}

func runCreate(cmd *cobra.Command, campaignID, legalEntityID string) error {
	in, err := contentStdin(cmd)
	if err != nil {
		return err
	}
	data, err := terminal.ReadStdin(in)
	if err != nil {
		return err
	}
	obj, err := terminal.ParseObject(data)
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	fields, err := content.LoadFields(c.BaseURL)
	if err != nil {
		return err
	}
	created, err := content.Create(c, campaignID, fields, data, obj, legalEntityID)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created content %s (%s, %s, %s)\n", created.ID, created.Channel, created.Language,
		created.Status)
	return nil
}
