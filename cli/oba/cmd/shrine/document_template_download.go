package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/documenttemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentTemplateDownloadHelp = `Writes the HTML of a document template to --output, or to <name>.html in the current directory.
An existing file is not overwritten unless --force is given.

  oba document template download <templateId> --output legal/nda/nda_nl.html --force`

func newDocumentTemplateDownloadCmd() *cobra.Command {
	var output string
	var force bool
	cmd := &cobra.Command{
		Use:   "download <id>",
		Short: "Write the HTML of a document template to a file",
		Long:  documentTemplateDownloadHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := documenttemplate.ParseID(args[0])
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			path, err := documenttemplate.Download(c, id, output, force)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Wrote %s\n", path)
			return nil
		},
	}
	cmd.Flags().StringVarP(&output, "output", "o", "", "the file to write (default <name>.html)")
	cmd.Flags().BoolVar(&force, "force", false, "overwrite an existing file")
	return cmd
}
