package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/legalentity"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const legalEntityUpdateHelp = `Changes only the fields whose flags are given; every other field keeps its stored value. An
empty value clears an optional field, for example --vat-number "". The short name, legal name and
country can be changed but not cleared.

  oba legal-entity update <id> --city Rotterdam --street ""`

func newLegalEntityUpdateCmd() *cobra.Command {
	var flags legalEntityFlags
	cmd := &cobra.Command{
		Use:   "update <id>",
		Short: "Change the given fields of a legal entity",
		Long:  legalEntityUpdateHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := legalentity.ParseID(args[0])
			if err != nil {
				return err
			}
			in := flags.input(cmd)
			if err := in.CheckUpdate(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			updated, err := legalentity.Update(c, id, in)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated legal entity %s\n", legalentity.Label(updated))
			return nil
		},
	}
	flags.bind(cmd, "", ` ("" clears it)`)
	return cmd
}
