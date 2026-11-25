package cyberscore

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const checkUpdateHelp = `Changes only the fields whose flags are given and keeps the others, the outcome and the
status included; 'check outcome' and 'check status' change those. --description "" and
--category "" clear them; --name, --expected and --weight cannot be empty.
  oba kazan cyberscore check update MFA enforced --weight 20
` + checkRefHelp

func newCheckUpdateCmd() *cobra.Command {
	var name, expected, description, category string
	var weight, sortOrder int
	cmd := &cobra.Command{
		Use:   "update <check>",
		Short: "Change the given fields of a check",
		Long:  checkUpdateHelp,
		RunE: func(cmd *cobra.Command, args []string) error {
			in := cyberscore.UpdateInput{
				Name:        changed(cmd, "name", name),
				Expected:    changed(cmd, "expected", expected),
				Weight:      changedInt(cmd, "weight", weight),
				Description: changed(cmd, "description", description),
				SortOrder:   changedInt(cmd, "sort-order", sortOrder),
				Category:    changed(cmd, "category", category),
			}
			if err := in.CheckUpdate(); err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			updated, err := cyberscore.UpdateCheck(c, checkArg(args), in)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated check %s\n", cyberscore.Label(updated))
			return nil
		},
	}
	f := cmd.Flags()
	f.StringVar(&name, "name", "", "new name")
	f.StringVar(&expected, "expected", "", "new expected result")
	f.IntVar(&weight, "weight", 0, "new weight, 1 to 100")
	f.StringVar(&description, "description", "", `new description ("" clears it)`)
	f.IntVar(&sortOrder, "sort-order", 0, "new position within its category, 0 to 9999")
	f.StringVar(&category, "category", "", `new category ("" clears it): `+strings.Join(cyberscore.Categories, ", "))
	return cmd
}
