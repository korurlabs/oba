package attachment

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kalkan/attachment"
	storage "github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const attachmentListHelp = `Lists the attachments of a page in upload order: NAME, SIZE (bytes), TYPE, CREATED, ID.

  oba kalkan attachment list --page isms/policies/access-control`

func newListCmd() *cobra.Command {
	var page pageFlag
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the attachments of a page",
		Long:  attachmentListHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runList(cmd, &page)
		},
	}
	page.register(cmd)
	return cmd
}

func runList(cmd *cobra.Command, page *pageFlag) error {
	path, err := page.normalized()
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	target, err := attachment.ResolveTarget(c, path)
	if err != nil {
		return err
	}
	attachments, err := storage.List(c, target.EntityType, target.EntityID)
	if err != nil {
		return err
	}
	return storage.PrintList(cmd.OutOrStdout(), attachments)
}
