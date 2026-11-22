package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/legalentity"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const legalEntityCreateHelp = `Creates a legal entity in the organization. --short-name, --legal-name and --country are
required; the other fields are optional. The short name is unique in the organization, case
ignored.

  oba legal-entity create --short-name "Korur B.V." --legal-name "Korur B.V." --country NLD \
    --registration-number 12345678 --city Amsterdam --governing-law "Dutch law" --court "Rechtbank Amsterdam"`

func newLegalEntityCreateCmd() *cobra.Command {
	var flags legalEntityFlags
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a legal entity",
		Long:  legalEntityCreateHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			in := flags.input(cmd)
			if err := in.CheckCreate(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			created, err := legalentity.Create(c, in)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created legal entity %s\n", legalentity.Label(created))
			return nil
		},
	}
	flags.bind(cmd, " (required)", "")
	return cmd
}
