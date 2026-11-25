package campaign

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/campaign"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const createHelp = `Creates one campaign in the current organization from one JSON object on stdin. The keys are
the API's field names and the values go to the API unchanged; a key the API does not know is
refused before anything is sent. Only name is required. startDate and endDate are dates such as
2027-03-01; the end may not be before the start.

Agents and scripts pass the object as a quoted heredoc: stdin does not show in 'ps', and the
quoted 'EOF' stops the shell from expanding $ and backticks inside it:

  oba otak campaign create <<'EOF'
  {"name": "De wacht", "description": "Post about the night watch",
   "startDate": "2027-03-01", "endDate": "2027-03-31"}
  EOF

People typing by hand write the object to a file and redirect it, because a heredoc typed at
the prompt lands in the shell history:

  oba otak campaign create < campaign.json

Add the posts with 'oba otak campaign content create <campaignId>'.`

func newCreateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a campaign from one JSON object on stdin",
		Long:  createHelp,
		Args:  cobra.NoArgs,
		RunE:  runCreate,
	}
	cmd.SetHelpFunc(fieldHelp(createHelp, campaign.CreateFields, true))
	return cmd
}

func runCreate(cmd *cobra.Command, _ []string) error {
	in, err := campaignStdin(cmd)
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
	fields, err := campaign.LoadFields(c.BaseURL, campaign.CreateFields)
	if err != nil {
		return err
	}
	created, err := campaign.Create(c, fields, data, obj)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created campaign %s (%s)\n", created.ID, guardrail.OneLine(created.Name))
	return nil
}
