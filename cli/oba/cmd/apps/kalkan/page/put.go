package page

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kalkan/page"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const pagePutHelp = `Writes the page at <path> in one language: creates it when the path names no page, otherwise
updates that language and keeps the others. Every parent must exist; a path of one slug creates a
root page, which needs the ADMIN role. --file - reads the body from stdin.

--compliance sets the compliance mark, --compliance=false clears it, and without the flag the
stored mark is kept. A page edited in Kalkan since the last upload is not written (KLK_STA_004,
exit 1) unless --force is given. With --json, prints the outcome the API sent: CREATED, UPDATED
or UNCHANGED, with the path and version.

  oba kalkan page put engineering/plans --title "Plans" --file plans.md
  cat plans.tr.md | oba kalkan page put engineering/plans --lang tr --title "Planlar" --file -`

func newPutCmd() *cobra.Command {
	var in page.PutInput
	var compliance bool
	cmd := &cobra.Command{
		Use:   "put <path>",
		Short: "Create or update one language of a page",
		Long:  pagePutHelp + "\n\n" + pagePathHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			put := in
			put.Path = args[0]
			if cmd.Flags().Changed("compliance") {
				put.Compliance = &compliance
			}
			return runPut(cmd, put)
		},
	}
	cmd.Flags().StringVar(&in.Lang, "lang", "en", "language of the title and body: tr or en")
	cmd.Flags().StringVar(&in.Title, "title", "", "the page title in that language (required)")
	cmd.Flags().StringVar(&in.File, "file", "", "the Markdown file of the body, - for stdin (required)")
	cmd.Flags().BoolVar(&compliance, "compliance", false,
		"set the compliance mark; --compliance=false clears it; left out, the mark is kept")
	cmd.Flags().BoolVar(&in.Force, "force", false, "overwrite a page edited in Kalkan since the last upload")
	return cmd
}

func runPut(cmd *cobra.Command, in page.PutInput) error {
	up, err := page.BuildUpsert(in, cmd.InOrStdin())
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	result, err := page.Put(c, up)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "%s page %s (version %d)\n", outcomeWord(result.Outcome), result.Path, result.Version)
	return nil
}

// outcomeWord turns the API's CREATED, UPDATED or UNCHANGED into the word that starts the line.
func outcomeWord(outcome string) string {
	if outcome == "" {
		return "Wrote"
	}
	return strings.ToUpper(outcome[:1]) + strings.ToLower(outcome[1:])
}
