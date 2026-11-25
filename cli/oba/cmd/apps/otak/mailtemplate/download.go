package mailtemplate

import (
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/mailtemplate"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const mailTemplateDownloadHelp = `Writes a mail template into the directory given with -o (default the current one) as
<name>.md, in the form 'upload' reads. An existing file is not overwritten unless --force is
given.`

func newDownloadCmd() *cobra.Command {
	var dir string
	var force bool
	cmd := &cobra.Command{
		Use:   "download <mailTemplateId>",
		Short: "Write a mail template to a .md file",
		Long:  mailTemplateDownloadHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDownload(cmd, strings.TrimSpace(args[0]), dir, force)
		},
	}
	cmd.Flags().StringVarP(&dir, "output", "o", ".", "the directory to write into")
	cmd.Flags().BoolVar(&force, "force", false, "overwrite an existing file")
	return cmd
}

func runDownload(cmd *cobra.Command, id, dir string, force bool) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	paths, err := mailtemplate.Download(c, id, dir, force)
	printWritten(cmd.OutOrStdout(), paths)
	return err
}

func printWritten(out io.Writer, paths []string) {
	for _, p := range paths {
		_, _ = fmt.Fprintf(out, "Wrote %s\n", p)
	}
}
