package attachment

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kalkan/attachment"
	storage "github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const attachmentAddHelp = `Adds local files to a page: --file <path>, repeated for more files, sent in one upload of at
most 12 MB together. The same content twice on one page is refused (STG_VAL_005).

  oba kalkan attachment add --page isms/policies/access-control --file policy.pdf --file review.png`

func newAddCmd() *cobra.Command {
	var page pageFlag
	var files []string
	cmd := &cobra.Command{
		Use:   "add",
		Short: "Add local files to a page",
		Long:  attachmentAddHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runAdd(cmd, &page, files)
		},
	}
	page.register(cmd)
	cmd.Flags().StringArrayVar(&files, "file", nil, "a local file to attach; repeat it for more files (required)")
	return cmd
}

func runAdd(cmd *cobra.Command, page *pageFlag, paths []string) error {
	path, err := page.normalized()
	if err != nil {
		return err
	}
	// The files are read and checked before any request, so that a bad path fails fast.
	files, err := attachment.ReadFiles(paths)
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
	added, err := storage.Add(c, target.EntityType, target.EntityID, files)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	for _, a := range added {
		_, _ = fmt.Fprintf(out, "Added attachment %s (%s) to %s\n", a.ID, guardrail.OneLine(a.FileName), target.Label)
	}
	return nil
}
