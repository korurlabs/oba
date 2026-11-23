package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// newOrgCmd builds oba org with its list and use subcommands.
func newOrgCmd() *cobra.Command {
	orgCmd := &cobra.Command{
		Use:   "org",
		Short: "List your organizations and choose the one you work in",
		Long: "The current organization is the one chosen with 'oba org use', stored in ~/.config/oba/config.json, " +
			"else the default organization of your account. App commands act in it.",
	}
	orgListCmd := &cobra.Command{
		Use:   "list",
		Short: "List your organizations; * marks the current one",
		Args:  cobra.NoArgs,
		RunE:  RunOrgList,
	}
	orgUseCmd := &cobra.Command{
		Use:   "use <name|id>",
		Short: "Choose the organization app commands act in",
		Long: "Matches the value against the organization ids exactly, else against the names without regard to case, " +
			"and stores the id in ~/.config/oba/config.json. A name that several organizations share is refused: " +
			"use the id instead.",
		Args: cobra.ExactArgs(1),
		RunE: runOrgUse,
	}
	orgCmd.AddCommand(orgListCmd, orgUseCmd)
	return orgCmd
}

// RunOrgList prints the signed-in user's organizations, * marking the current one; with --json
// it prints them as the API sent them. oba ulak org list runs it too, so that both show one
// truth.
func RunOrgList(cmd *cobra.Command, _ []string) error {
	creds, user, err := session.CurrentUser()
	if err != nil {
		return err
	}
	currentID, err := session.CurrentOrgID(creds.DefaultOrgID, user.Organizations)
	if err != nil {
		return err
	}
	if err := recordOrganizations(); err != nil {
		return err
	}
	return organization.PrintList(cmd.OutOrStdout(), user.Organizations, currentID)
}

func runOrgUse(cmd *cobra.Command, args []string) error {
	_, user, err := session.CurrentUser()
	if err != nil {
		return err
	}
	org, err := organization.Match(user.Organizations, args[0])
	if err != nil {
		return err
	}

	settings, err := config.Load()
	if err != nil {
		return err
	}
	settings.OrgID = org.ID
	if err := config.Save(settings); err != nil {
		return err
	}
	if err := recordOrganization(org.ID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Current organization: %s (%s)\n", org.Name, org.ID)
	return nil
}
