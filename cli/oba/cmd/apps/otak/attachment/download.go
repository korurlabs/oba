package attachment

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/attachment"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const attachmentDownloadHelp = `Writes one attachment into the directory given with -o (default the current one) under its
own file name. An existing file is not overwritten unless --force is given. The id must be an
attachment of the given outreach, relation or content.

  oba otak attachment download --content <contentId> --id <attachmentId> -o images/`

func newDownloadCmd() *cobra.Command {
	var target targetFlags
	var id, dir string
	var force bool
	cmd := &cobra.Command{
		Use:   "download",
		Short: "Write an attachment of an outreach's mail, a relation or a campaign content to a file",
		Long:  attachmentDownloadHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runAttachmentDownload(cmd, &target, strings.TrimSpace(id), dir, force)
		},
	}
	target.register(cmd)
	cmd.Flags().StringVar(&id, "id", "", "the attachment id, from 'oba otak attachment list' (required)")
	cmd.Flags().StringVarP(&dir, "output", "o", ".", "the directory to write into")
	cmd.Flags().BoolVar(&force, "force", false, "overwrite an existing file")
	_ = cmd.MarkFlagRequired("id")
	return cmd
}

func runAttachmentDownload(cmd *cobra.Command, flags *targetFlags, id, dir string, force bool) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	target, err := flags.resolve(c)
	if err != nil {
		return err
	}
	path, err := attachment.Download(c, target, id, dir, force)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Wrote %s\n", path)
	return nil
}
