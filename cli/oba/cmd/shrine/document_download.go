package shrine

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/document"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const documentDownloadHelp = `Writes the HTML of a document to --output, or to <title>.html in the current directory. An
existing file is not overwritten unless --force is given. Edit it and send it back with
'oba document upload <id> <file.html>'.

  oba document download <documentId> --output work/nda-acme.html`

func newDocumentDownloadCmd() *cobra.Command {
	var output string
	var force bool
	cmd := &cobra.Command{
		Use:   "download <id>",
		Short: "Write the HTML of a document to a file",
		Long:  documentDownloadHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := document.ParseID(args[0])
			if err != nil {
				return err
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			path, err := document.Download(c, id, output, force)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Wrote %s\n", path)
			return nil
		},
	}
	cmd.Flags().StringVarP(&output, "output", "o", "", "the file to write (default <title>.html)")
	cmd.Flags().BoolVar(&force, "force", false, "overwrite an existing file")
	return cmd
}
