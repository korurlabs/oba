package attachment

import (
	"bufio"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kalkan/attachment"
	storage "github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const attachmentDeleteHelp = `Deletes one attachment of a page after asking for confirmation; without a terminal to ask on,
pass --yes. The id must be an attachment of the given page.

  oba kalkan attachment delete --page isms/policies/access-control --id <attachmentId>`

func newDeleteCmd() *cobra.Command {
	var page pageFlag
	var id string
	var yes bool
	cmd := &cobra.Command{
		Use:   "delete",
		Short: "Delete an attachment of a page",
		Long:  attachmentDeleteHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runDelete(cmd, &page, strings.TrimSpace(id), yes)
		},
	}
	page.register(cmd)
	cmd.Flags().StringVar(&id, "id", "", "the attachment id, from 'oba kalkan attachment list' (required)")
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	_ = cmd.MarkFlagRequired("id")
	return cmd
}

func runDelete(cmd *cobra.Command, page *pageFlag, id string, yes bool) error {
	path, err := page.normalized()
	if err != nil {
		return err
	}
	if !yes && !stdinIsTerminal() {
		return terminal.ErrDeleteNeedsYes
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	target, err := attachment.ResolveTarget(c, path)
	if err != nil {
		return err
	}
	found, err := attachment.Find(c, target, id)
	if err != nil {
		return err
	}
	name := guardrail.OneLine(found.FileName)
	out := cmd.OutOrStdout()
	if !yes {
		src := cmd.InOrStdin()
		question := fmt.Sprintf("Delete attachment %s of %s?", name, target.Label)
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, question)
		if err != nil {
			return err
		}
		if !confirmed {
			// The lookups before the question are not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := storage.Delete(c, found); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Deleted attachment %s (%s) of %s\n", found.ID, name, target.Label)
	return nil
}
