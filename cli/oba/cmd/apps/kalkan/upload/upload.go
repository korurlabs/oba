// Package upload builds oba kalkan upload: write a directory of Markdown files to Kalkan as a
// tree of pages.
package upload

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kalkan/upload"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const uploadHelp = `Writes every Markdown page of <dir> under the page path <target>, parents first, and uploads
the files they link to, once per page. Without arguments, uploads every entry of the kalkan.yaml
in the current directory or at the git root; its organization (an id or a name) is used unless
--org is given.

Layout: a/b.md is page a/b in --lang; a/b.tr.md and a/b.en.md set the language; a/index.md or
a/README.md is page a; a directory with Markdown but no index file becomes a page titled by its
slug, and an existing one is left as it is. Hidden entries and directories named handoffs are
not read. Every file name must be a slug, and a path at most 10 slugs deep.

Each page is reported as created, updated, unchanged, conflict, denied, failed or skipped. A page
edited in Kalkan since the last upload is a conflict and is not written unless --force is given;
the pages below a page that was denied or failed are skipped, and the other pages are written,
also those below a conflict, since that page exists in Kalkan. A refused sign-in ends the run.
--dry-run reports the outcome of every page without writing or uploading anything. With --json,
prints {"counts":{...},"pages":[...]} also when the run fails.

Exit status: 0 when every page was created, updated or unchanged; else the first of 1 (a
failure), 3 (access denied), 2 (a page not found or hidden from you) and 4 (a conflict) that
occurred.

  oba kalkan upload docs/plans engineering/plans
  oba kalkan upload --dry-run`

// NewCmd builds oba kalkan upload.
func NewCmd() *cobra.Command {
	var lang string
	var dryRun, force bool
	cmd := &cobra.Command{
		Use:   "upload [<dir> <target>]",
		Short: "Create or update every page of a directory under a target path",
		Long:  uploadHelp,
		Args: func(_ *cobra.Command, args []string) error {
			if len(args) != 0 && len(args) != 2 {
				return fmt.Errorf("name both <dir> and <target>, or neither to use %s", upload.ProjectFileName)
			}
			return nil
		},
		RunE: func(cmd *cobra.Command, args []string) error {
			in := upload.BundlesInput{Lang: lang}
			if len(args) == 2 {
				in.Dir, in.Target = args[0], args[1]
			}
			return runUpload(cmd, in, dryRun, force)
		},
	}
	cmd.Flags().StringVar(&lang, "lang", "en", "language of files without a .tr or .en suffix: tr or en")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "report the outcome of every page without writing")
	cmd.Flags().BoolVar(&force, "force", false, "overwrite pages edited in Kalkan since the last upload")
	return cmd
}

func runUpload(cmd *cobra.Command, in upload.BundlesInput, dryRun, force bool) error {
	cwd, err := os.Getwd()
	if err != nil {
		return keepReport(upload.EmptyReport(), fmt.Errorf("read the current directory: %w", err))
	}
	in.WorkDir = cwd
	bundles, organization, err := upload.ReadBundles(in)
	if err != nil {
		return keepReport(upload.EmptyReport(), err)
	}
	session.UseOrganizationUnlessSet(organization)

	run := upload.RunInput{Bundles: bundles, DryRun: dryRun, Force: force}
	var report upload.Report
	if c, err := session.OrganizationClient(); err != nil {
		report = upload.NotRun(run, err)
	} else {
		report = upload.Run(c, run)
	}
	if !jsonout.Enabled() {
		upload.PrintReport(cmd.OutOrStdout(), report)
	}
	upload.PrintErrors(cmd.ErrOrStderr(), report)
	if status := upload.ExitStatus(report); status != 0 {
		return keepReport(report, &exitcode.Error{Code: status})
	}
	return jsonresult.SetValue(report)
}

// keepReport makes report the --json result also of a failed run, so that a script learns what
// was written together with the non-zero exit status, and returns cause.
func keepReport(report upload.Report, cause error) error {
	encoded, err := json.Marshal(report)
	if err != nil {
		return fmt.Errorf("failed to encode the JSON output: %w", err)
	}
	if err := jsonresult.SetPartial(encoded); err != nil {
		return err
	}
	return cause
}
